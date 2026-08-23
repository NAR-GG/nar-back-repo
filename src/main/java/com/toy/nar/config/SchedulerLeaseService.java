package com.toy.nar.config;

import com.toy.nar.domain.sync.repository.SchedulerLeaseRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 스케줄러 리더십. {@code @Scheduled} 잡은 리더인 파드에서만 돈다.
 *
 * <p>목표는 스케줄러의 무중단 교체다. 지금은 replicas 1 + Recreate 로 "물리적으로 하나"를
 * 보장하는 대가로 배포마다 38~48초 폴링 공백이 생긴다. 이 리스가 "논리적으로 하나"를 보장하면
 * RollingUpdate 로 바꿔도 이중 실행이 없다 — 새 파드는 떠서 대기하고, 구 파드가 종료 훅에서
 * 리스를 놓는 순간 다음 갱신 주기(5초 이내)에 이어받는다.</p>
 *
 * <p><b>이 클래스는 두 단계 전환의 1단계다.</b> Recreate 아래에서는 파드가 하나뿐이라 리스가
 * 항상 즉시 잡힌다 — 켜도 동작이 바뀌지 않는 상태에서 획득·갱신·해제 로그를 검증하고,
 * 깨끗한 것을 확인한 뒤에 RollingUpdate 로 바꾼다(2단계, 별도 PR).</p>
 *
 * <p>갱신 루프는 자기 전용 스레드를 쓴다 — 게이트가 걸린 {@code TaskScheduler} 를 타면
 * "리더가 아니라 갱신을 못 해서 영원히 리더가 못 되는" 교착이 된다.</p>
 *
 * <h4>시계와 펜싱</h4>
 *
 * <p>진실은 DB 시계다(NOW(3) 기준 만료). 로컬에는 근사치({@link #leaderUntil})만 두는데,
 * 갱신 성공 시각 + TTL 에서 <b>안전 마진 2초를 뺀다</b> — 로컬 시계가 DB 보다 느리면 DB 는
 * 만료로 보고 후임이 리스를 가져갔는데 우리는 아직 리더라고 믿는 겹침이 생길 수 있어서다.
 * 갱신이 계속 실패하면(DB 장애 등) 이 근사치가 지나는 순간 스스로 리더를 내려놓는다 —
 * 후임이 있을지 모르는 상태에서 발송을 계속하는 것보다 멈추는 쪽이 싸다. 어차피 잡 대부분이
 * DB 를 쓰므로 DB 장애 중에 돌 수 있는 잡도 거의 없다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true")
public class SchedulerLeaseService {

	/** DB 에 기록되는 리스 수명. 갱신 3회(15초/5초)를 놓쳐야 뺏긴다. */
	private static final Duration TTL = Duration.ofSeconds(15);
	/** 갱신 주기. 정상 종료 시 후임 인수인계도 최대 이 시간이다. */
	private static final Duration RENEW_INTERVAL = Duration.ofSeconds(5);
	/** 로컬 근사치에서 빼는 안전 마진(시계 스큐 대비). */
	private static final Duration FENCE_MARGIN = Duration.ofSeconds(2);

	private final SchedulerLeaseRepository leaseRepository;
	private final Clock clock;

	/**
	 * 리스 전체 스위치. 꺼져 있으면(기본) 항상 리더로 취급한다 — 오늘까지의 동작 그대로다.
	 * prod 스케줄러 파드에서 env {@code APP_SCHEDULING_LEASE_ENABLED} 로 켠다.
	 */
	@Value("${app.scheduling.lease.enabled:false}")
	private boolean leaseEnabled;

	/** 파드 이름(k8s 가 HOSTNAME 으로 넣어 준다). 없으면 UUID — 로컬 실행 등. */
	private final String holder;

	/** 이 시각 전까지는 리더다(로컬 근사치). EPOCH = 리더 아님. */
	private volatile Instant leaderUntil = Instant.EPOCH;

	/** 전이 로그용. 매 갱신마다 찍으면 5초마다 도배된다. */
	private volatile boolean wasLeader;

	private final ScheduledExecutorService renewer =
			Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, "scheduler-lease-renewer");
				thread.setDaemon(true);
				return thread;
			});

	@org.springframework.beans.factory.annotation.Autowired
	public SchedulerLeaseService(SchedulerLeaseRepository leaseRepository) {
		this(leaseRepository, Clock.systemUTC());
	}

	/** 테스트 전용 — 시계를 고정해 펜싱(TTL 경과 시 자진 사퇴)을 결정론적으로 검증한다. */
	SchedulerLeaseService(SchedulerLeaseRepository leaseRepository, Clock clock) {
		this.leaseRepository = leaseRepository;
		this.clock = clock;
		String hostname = System.getenv("HOSTNAME");
		this.holder = hostname != null && !hostname.isBlank()
				? hostname
				: "local-" + UUID.randomUUID();
	}

	@PostConstruct
	void start() {
		if (!leaseEnabled) {
			log.info("[scheduler-lease] 리스 비활성 — 항상 리더로 동작한다 (기존 동작)");
			return;
		}
		log.info("[scheduler-lease] 리스 활성 holder={} ttl={}s renew={}s",
				holder, TTL.toSeconds(), RENEW_INTERVAL.toSeconds());
		renewer.scheduleWithFixedDelay(this::renew, 0, RENEW_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
	}

	/** 지금 이 파드가 잡을 돌려도 되는가. 잡 발화마다 불리므로 DB 를 건드리지 않는다. */
	public boolean isLeader() {
		if (!leaseEnabled) {
			return true;
		}
		return clock.instant().isBefore(leaderUntil);
	}

	void renew() {
		try {
			if (leaseRepository.acquireOrRenew(holder, TTL)) {
				leaderUntil = clock.instant().plus(TTL).minus(FENCE_MARGIN);
				if (!wasLeader) {
					wasLeader = true;
					log.info("[scheduler-lease] 리더 획득 holder={}", holder);
				}
			} else {
				// 다른 파드가 살아서 갱신 중이다. 즉시 내려놓는다.
				leaderUntil = Instant.EPOCH;
				if (wasLeader) {
					wasLeader = false;
					log.warn("[scheduler-lease] 리더 상실 — 다른 파드가 리스를 잡고 있다 holder={}", holder);
				}
			}
		} catch (Exception e) {
			// DB 장애. leaderUntil 을 건드리지 않는다 — 남은 근사치가 지나면 스스로 내려놓는다.
			log.warn("[scheduler-lease] 리스 갱신 실패 (남은 리더십 {}ms): {}",
					Math.max(0, leaderUntil.toEpochMilli() - clock.instant().toEpochMilli()),
					e.getMessage());
			if (wasLeader && !isLeader()) {
				wasLeader = false;
				log.warn("[scheduler-lease] 리더 상실 — 갱신 실패가 TTL 을 넘겼다 holder={}", holder);
			}
		}
	}

	@PreDestroy
	void stop() {
		renewer.shutdownNow();
		if (!leaseEnabled) {
			return;
		}
		try {
			// 즉시 만료시켜 후임이 TTL(15초)을 기다리지 않게 한다. 다음 갱신 주기(5초) 안에 이어받는다.
			leaseRepository.release(holder);
			log.info("[scheduler-lease] 리스 반납 holder={}", holder);
		} catch (Exception e) {
			log.warn("[scheduler-lease] 리스 반납 실패 — 후임은 TTL 만료를 기다린다: {}", e.getMessage());
		}
	}
}
