package com.toy.nar.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * Live Activity 토큰 조회/정리 쿼리 검증.
 *
 * <p>발송 경로는 전부 mock 으로 덮여 있어 JPQL 자체는 한 번도 실행되지 않는다.
 * 여기서 실제 EntityManager 로 돌려 쿼리가 파싱되고 의도대로 동작하는지 확인한다
 * (틀리면 프로덕션에서 리포지토리 빈 생성 시점에 기동이 실패한다).</p>
 */
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
// 이 패키지에 @SpringBootConfiguration 을 품은 MySQL 통합 테스트가 여럿 있어
// 자동 탐색이 "multiple @SpringBootConfiguration" 으로 실패한다. 설정을 직접 지정한다.
@ContextConfiguration(classes = LiveActivityTokenRepositoryTest.TestJpaConfiguration.class)
class LiveActivityTokenRepositoryTest {

	@SpringBootConfiguration
	@EnableAutoConfiguration
	// 엔티티는 도메인 전체를 스캔한다(서로 물려 있어 일부만 담으면 매핑이 깨진다).
	// 리포지토리 스캔만 member 로 좁혀 Elasticsearch 리포지토리를 피한다.
	@EntityScan(basePackages = "com.toy.nar.domain")
	@EnableJpaRepositories(basePackageClasses = LiveActivityTokenRepository.class)
	static class TestJpaConfiguration {
	}

	@Autowired
	private LiveActivityTokenRepository tokenRepository;

	@PersistenceContext
	private EntityManager em;

	@BeforeEach
	void seed() {
		exec("INSERT INTO member (id, name, tag, role, created_at, quiet_hours_enabled, quiet_start_time, quiet_end_time)"
				+ " VALUES (1, '테스터', '0001', 'USER', CURRENT_TIMESTAMP, false, '01:00:00', '08:00:00')");
		exec("INSERT INTO member (id, name, tag, role, created_at, quiet_hours_enabled, quiet_start_time, quiet_end_time)"
				+ " VALUES (2, '테스터2', '0002', 'USER', CURRENT_TIMESTAMP, false, '01:00:00', '08:00:00')");
		// match-1: 활성 2개 + 비활성 1개, match-2: 활성 1개
		insertToken(1L, "match-1", "tok-a", 1L, true);
		insertToken(2L, "match-1", "tok-b", 2L, true);
		insertToken(3L, "match-1", "tok-dead", 1L, false);
		insertToken(4L, "match-2", "tok-other", 1L, true);
		em.flush();
		em.clear();
	}

	@Autowired
	private LiveActivityStartTokenRepository startTokenRepository;

	@Test
	void push_to_start_는_구독자에게_보내되_카드가_이미_있으면_제외한다() {
		subscribeBothMembersToTeam10();

		// match-1 은 seed 에서 두 회원 모두 활성 카드를 갖고 있다 → 전원 제외.
		assertThat(startTokenRepository.findStartTargets("match-1", 10L, null)).isEmpty();

		// match-3 은 아무도 카드가 없다 → 구독자 전원이 대상.
		assertThat(startTokenRepository.findStartTargets("match-3", 10L, null))
				.extracting(LiveActivityStartTokenRepository.StartTargetRow::getPushToken)
				.containsExactlyInAnyOrder("start-1", "start-2");
	}

	@Test
	void 구독하지_않은_팀_경기는_대상이_아니다() {
		subscribeBothMembersToTeam10();

		assertThat(startTokenRepository.findStartTargets("match-3", 99L, 98L)).isEmpty();
	}

	@Test
	void 세트_시작_알림을_끈_구독자에게는_카드를_만들지_않는다() {
		// 알림을 끈 사람에게 잠금화면 카드를 띄우면 안 된다.
		exec("INSERT INTO teams (team_id, team_name, team_code) VALUES (10, 'T1', 'T1')");
		insertStartToken(1L, 1L, "start-1");
		exec("INSERT INTO member_team_notification_subscription"
				+ " (id, member_id, team_id, set_start_enabled, set_end_enabled,"
				+ "  live_event_enabled, created_at, updated_at)"
				+ " VALUES (1, 1, 10, false, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
		em.flush();
		em.clear();

		assertThat(startTokenRepository.findStartTargets("match-3", 10L, null)).isEmpty();
	}

	private void subscribeBothMembersToTeam10() {
		exec("INSERT INTO teams (team_id, team_name, team_code) VALUES (10, 'T1', 'T1')");
		insertStartToken(1L, 1L, "start-1");
		insertStartToken(2L, 2L, "start-2");
		for (long memberId = 1; memberId <= 2; memberId++) {
			exec("INSERT INTO member_team_notification_subscription"
					+ " (id, member_id, team_id, set_start_enabled, set_end_enabled,"
					+ "  live_event_enabled, created_at, updated_at)"
					+ " VALUES (" + memberId + ", " + memberId + ", 10,"
					+ " true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
		}
		em.flush();
		em.clear();
	}

	private void insertStartToken(long id, long memberId, String pushToken) {
		exec("INSERT INTO live_activity_start_token"
				+ " (id, member_id, push_token, active, created_at, updated_at)"
				+ " VALUES (" + id + ", " + memberId + ", '" + pushToken + "', true,"
				+ " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
	}

	@Test
	void 매치의_활성_토큰만_돌려준다() {
		assertThat(tokenRepository.findActivePushTokensByMatchId("match-1"))
				.containsExactlyInAnyOrder("tok-a", "tok-b");
	}

	@Test
	void 다른_매치의_토큰은_섞이지_않는다() {
		assertThat(tokenRepository.findActivePushTokensByMatchId("match-2"))
				.containsExactly("tok-other");
	}

	@Test
	void 카드가_없는_매치는_빈_목록() {
		assertThat(tokenRepository.findActivePushTokensByMatchId("match-없음")).isEmpty();
	}

	@Test
	void 지정한_토큰만_비활성화한다() {
		int updated = tokenRepository.deactivateByPushTokenIn(java.util.List.of("tok-a"));

		assertThat(updated).isEqualTo(1);
		assertThat(tokenRepository.findActivePushTokensByMatchId("match-1")).containsExactly("tok-b");
	}

	@Test
	void 여러_토큰을_한_번에_비활성화한다() {
		assertThat(tokenRepository.deactivateByPushTokenIn(java.util.List.of("tok-a", "tok-b"))).isEqualTo(2);
		assertThat(tokenRepository.findActivePushTokensByMatchId("match-1")).isEmpty();
		// 같은 회원의 다른 매치 카드는 살아 있어야 한다.
		assertThat(tokenRepository.findActivePushTokensByMatchId("match-2")).containsExactly("tok-other");
	}

	@Test
	void 매치_단위로_한_번에_비활성화한다() {
		assertThat(tokenRepository.deactivateAllByMatchId("match-1")).isEqualTo(2);

		assertThat(tokenRepository.findActivePushTokensByMatchId("match-1")).isEmpty();
		// 다른 매치는 건드리지 않는다.
		assertThat(tokenRepository.findActivePushTokensByMatchId("match-2")).containsExactly("tok-other");
	}

	@Test
	void 이미_비활성인_토큰은_다시_세지_않는다() {
		// active = true 조건이 빠지면 이미 죽은 tok-dead 까지 세어 3 이 나온다.
		assertThat(tokenRepository.deactivateAllByMatchId("match-1")).isEqualTo(2);
		assertThat(tokenRepository.deactivateAllByMatchId("match-1")).isZero();
	}

	@Test
	void 푸시_토큰으로_단건_조회한다() {
		assertThat(tokenRepository.findByPushToken("tok-a"))
				.get()
				.satisfies(token -> {
					assertThat(token.getMatchId()).isEqualTo("match-1");
					assertThat(token.isActive()).isTrue();
				});
		assertThat(tokenRepository.findByPushToken("없는토큰")).isEmpty();
	}

	@Test
	void 재등록하면_매치가_갱신되고_다시_활성화된다() {
		var token = tokenRepository.findByPushToken("tok-dead").orElseThrow();
		token.reactivate(token.getMember(), "match-3");
		tokenRepository.flush();
		em.clear();

		assertThat(tokenRepository.findActivePushTokensByMatchId("match-3")).containsExactly("tok-dead");
		assertThat(tokenRepository.findActivePushTokensByMatchId("match-1"))
				.containsExactlyInAnyOrder("tok-a", "tok-b");
	}

	private void insertToken(long id, String matchId, String pushToken, long memberId, boolean active) {
		exec("INSERT INTO live_activity_token"
				+ " (id, match_id, push_token, member_id, active, created_at, updated_at)"
				+ " VALUES (" + id + ", '" + matchId + "', '" + pushToken + "', " + memberId + ", "
				+ active + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
	}

	private void exec(String sql) {
		em.createNativeQuery(sql).executeUpdate();
	}
}
