package com.toy.nar.config;

import com.toy.nar.domain.sync.repository.SchedulerLeaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러 리더 리스. 두 벌이 돌면 라이브 폴링·푸시가 이중으로 나가므로,
 * 리스가 "논리적으로 하나"를 보장해야 Recreate(물리적으로 하나)를 걷어낼 수 있다.
 *
 * <p>갱신 루프는 실제 스레드로 돌리지 않는다 — {@code renew()} 를 직접 부르고 시계를 손으로
 * 움직여, 시간에 걸린 판정(펜싱·TTL)을 결정론적으로 검증한다.</p>
 */
class SchedulerLeaseServiceTest {

	/** 손으로 움직이는 시계. */
	private static class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-08-23T12:00:00Z");

		void advance(Duration amount) {
			now = now.plus(amount);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}

	/**
	 * 리스 테이블의 인메모리 대역. UPDATE ... WHERE (holder = me OR expired) 의 의미를
	 * 서비스와 같은 시계로 흉내낸다.
	 */
	private static class FakeLeaseRepository implements SchedulerLeaseRepository {
		private final MutableClock clock;
		private String holder = "";
		private Instant expiresAt = Instant.EPOCH;
		private boolean fail;
		private int releaseCalls;

		FakeLeaseRepository(MutableClock clock) {
			this.clock = clock;
		}

		@Override
		public boolean acquireOrRenew(String candidate, Duration ttl) {
			if (fail) {
				throw new RuntimeException("db down");
			}
			if (candidate.equals(holder) || expiresAt.isBefore(clock.instant())) {
				holder = candidate;
				expiresAt = clock.instant().plus(ttl);
				return true;
			}
			return false;
		}

		@Override
		public void release(String candidate) {
			releaseCalls++;
			if (candidate.equals(holder)) {
				expiresAt = clock.instant().minusSeconds(1);
			}
		}

		/** 다른 파드가 리스를 잡고 갱신 중인 상황을 만든다. */
		void heldByOther() {
			holder = "other-pod";
			expiresAt = clock.instant().plus(Duration.ofSeconds(15));
		}
	}

	private MutableClock clock;
	private FakeLeaseRepository repository;
	private SchedulerLeaseService service;

	@BeforeEach
	void setUp() {
		clock = new MutableClock();
		repository = new FakeLeaseRepository(clock);
		service = new SchedulerLeaseService(repository, clock);
		ReflectionTestUtils.setField(service, "leaseEnabled", true);
	}

	@Test
	@DisplayName("리스가 꺼져 있으면 항상 리더다 — 오늘까지의 동작 그대로")
	void alwaysLeaderWhenDisabled() {
		ReflectionTestUtils.setField(service, "leaseEnabled", false);

		assertThat(service.isLeader()).isTrue();
	}

	@Test
	@DisplayName("리스를 획득하면 리더가 된다")
	void becomesLeaderOnAcquire() {
		assertThat(service.isLeader()).isFalse();

		service.renew();

		assertThat(service.isLeader()).isTrue();
	}

	@Test
	@DisplayName("다른 파드가 리스를 잡고 있으면 리더가 되지 못한다")
	void staysFollowerWhenHeldByOther() {
		repository.heldByOther();

		service.renew();

		assertThat(service.isLeader()).isFalse();
	}

	@Test
	@DisplayName("갱신이 계속되면 리더십이 유지된다")
	void keepsLeadershipWhileRenewing() {
		service.renew();
		for (int i = 0; i < 10; i++) {
			clock.advance(Duration.ofSeconds(5));
			service.renew();
			assertThat(service.isLeader()).isTrue();
		}
	}

	@Test
	@DisplayName("갱신이 실패해도 TTL-마진 안에서는 리더를 유지한다 — 일시 장애로 잡을 세우지 않는다")
	void toleratesTransientRenewFailure() {
		service.renew();
		repository.fail = true;

		clock.advance(Duration.ofSeconds(5));
		service.renew(); // 실패

		assertThat(service.isLeader()).isTrue();
	}

	@Test
	@DisplayName("갱신 실패가 이어지면 TTL-마진(13초)이 지나는 순간 스스로 리더를 내려놓는다 — 펜싱")
	void fencesItselfAfterProlongedFailure() {
		service.renew();
		repository.fail = true;

		// TTL 15s - 마진 2s = 13s. 그 직전까지는 리더.
		clock.advance(Duration.ofSeconds(12));
		service.renew();
		assertThat(service.isLeader()).isTrue();

		clock.advance(Duration.ofSeconds(2));
		service.renew();
		assertThat(service.isLeader()).isFalse();
	}

	@Test
	@DisplayName("다른 파드에게 뺏기면 즉시 내려놓는다 — TTL 을 기다리지 않는다")
	void dropsImmediatelyWhenStolen() {
		service.renew();
		assertThat(service.isLeader()).isTrue();

		repository.heldByOther();
		service.renew();

		assertThat(service.isLeader()).isFalse();
	}

	@Test
	@DisplayName("종료 시 리스를 반납한다 — 후임이 TTL 을 기다리지 않게")
	void releasesOnStop() {
		service.renew();

		service.stop();

		assertThat(repository.releaseCalls).isEqualTo(1);
		// 반납 뒤에는 다른 파드가 즉시 획득할 수 있다.
		assertThat(repository.acquireOrRenew("successor", Duration.ofSeconds(15))).isTrue();
	}

	@Test
	@DisplayName("리스가 꺼져 있으면 종료 시 반납도 하지 않는다")
	void doesNotReleaseWhenDisabled() {
		ReflectionTestUtils.setField(service, "leaseEnabled", false);

		service.stop();

		assertThat(repository.releaseCalls).isZero();
	}

	@Test
	@DisplayName("빼앗긴 뒤 상대가 사라지면 다시 획득한다")
	void reacquiresAfterOtherExpires() {
		repository.heldByOther();
		service.renew();
		assertThat(service.isLeader()).isFalse();

		// 상대가 죽어 갱신을 멈춘다 → TTL 이 지나면 리스가 만료된다.
		clock.advance(Duration.ofSeconds(16));
		service.renew();

		assertThat(service.isLeader()).isTrue();
	}
}
