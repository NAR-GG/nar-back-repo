package com.toy.nar.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import com.toy.nar.domain.member.entity.MemberDevice;

/**
 * 라이브 이벤트 종류별 토글 조회를 실제 EntityManager 로 검증한다.
 *
 * <p>발송 경로 테스트는 리포지토리를 전부 mock 하므로 이 JPQL 은 그쪽에서 한 번도 실행되지
 * 않는다. 여기서 돌려야 파싱과 동작을 확인할 수 있다 — 틀리면 프로덕션에서 라이브 이벤트
 * 푸시가 통째로 안 나가거나 기동이 실패한다.</p>
 */
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@ContextConfiguration(classes = MemberDeviceLiveEventToggleTest.TestJpaConfiguration.class)
class MemberDeviceLiveEventToggleTest {

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EntityScan(basePackages = "com.toy.nar.domain")
	@EnableJpaRepositories(basePackageClasses = MemberDeviceRepository.class)
	static class TestJpaConfiguration {
	}

	private static final String MATCH_ID = "match-1";

	@Autowired
	private MemberDeviceRepository deviceRepository;

	@PersistenceContext
	private EntityManager em;

	/**
	 * member 1 — 킬만 끔, member 2 — 전부 켬, member 3 — 마스터를 끔.
	 */
	@BeforeEach
	void seed() {
		insertMember(1L, "킬만끈사람");
		insertMember(2L, "전부켠사람");
		insertMember(3L, "마스터끈사람");
		insertDevice(1L, 1L, "token-1");
		insertDevice(2L, 2L, "token-2");
		insertDevice(3L, 3L, "token-3");
		insertMatchSubscription(1L, 1L, true, false, true, true, true, true);
		insertMatchSubscription(2L, 2L, true, true, true, true, true, true);
		insertMatchSubscription(3L, 3L, false, true, true, true, true, true);
		em.flush();
		em.clear();
	}

	@Test
	@DisplayName("킬을 끈 회원은 KILL 대상에서 빠지고 BARON 대상에는 남는다")
	void 종류별_토글이_적용된다() {
		assertThat(memberIdsOf("LIVE_EVENT", "KILL")).containsExactly(2L);
		assertThat(memberIdsOf("LIVE_EVENT", "BARON")).containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	@DisplayName("마스터 스위치를 끄면 하위 토글이 켜져 있어도 제외된다")
	void 마스터가_꺼지면_제외된다() {
		// member 3 은 하위 5종이 전부 ON 인데 live_event_enabled 만 false 다.
		assertThat(memberIdsOf("LIVE_EVENT", "BARON")).doesNotContain(3L);
	}

	/**
	 * 종류를 모르는 이벤트(앞으로 추가될 아타칸·공허 유충 등)는 마스터 스위치만 보고 보내야 한다.
	 * 컬럼이 없다는 이유로 알림이 조용히 사라지면 안 된다.
	 */
	@Test
	@DisplayName("종류가 null 이면 마스터만 보고 보낸다")
	void 종류가_null_이면_마스터만_본다() {
		assertThat(memberIdsOf("LIVE_EVENT", null)).containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	@DisplayName("컬럼이 없는 새 종류가 들어와도 마스터만 보고 보낸다")
	void 모르는_종류도_마스터만_본다() {
		assertThat(memberIdsOf("LIVE_EVENT", "ATAKHAN")).containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	@DisplayName("세트 이벤트는 종류와 무관하게 기존 토글만 본다")
	void 세트_이벤트는_종류를_보지_않는다() {
		// member 1 은 set_start_enabled=true, member 3 도 true 다(마스터만 껐다).
		assertThat(memberIdsOf("SET_START", null)).containsExactlyInAnyOrder(1L, 2L, 3L);
		assertThat(memberIdsOf("SET_START", "KILL")).containsExactlyInAnyOrder(1L, 2L, 3L);
	}

	@Test
	@DisplayName("팀 구독 쿼리도 같은 규칙으로 동작한다")
	void 팀_구독도_같다() {
		exec("INSERT INTO teams (team_id, team_name, team_code) VALUES (10, 'T1', 'T1')");
		insertTeamSubscription(1L, 1L, 10L, true, false);
		insertTeamSubscription(2L, 2L, 10L, true, true);
		em.flush();
		em.clear();

		assertThat(memberIdsOfTeam(10L, "LIVE_EVENT", "KILL")).containsExactly(2L);
		assertThat(memberIdsOfTeam(10L, "LIVE_EVENT", "BARON")).containsExactlyInAnyOrder(1L, 2L);
		assertThat(memberIdsOfTeam(10L, "LIVE_EVENT", null)).containsExactlyInAnyOrder(1L, 2L);
	}

	private List<Long> memberIdsOf(String eventType, String eventSubType) {
		return deviceRepository.findActiveDevicesBySubscribedMatchId(MATCH_ID, eventType, eventSubType)
				.stream()
				.map(device -> device.getMember().getId())
				.sorted()
				.toList();
	}

	private List<Long> memberIdsOfTeam(Long teamId, String eventType, String eventSubType) {
		return deviceRepository.findActiveDevicesBySubscribedTeamId(teamId, eventType, eventSubType)
				.stream()
				.map(MemberDevice::getMember)
				.map(member -> member.getId())
				.sorted()
				.toList();
	}

	private void insertMember(long id, String name) {
		exec("INSERT INTO member (id, name, tag, role, created_at, quiet_hours_enabled,"
				+ " quiet_start_time, quiet_end_time)"
				+ " VALUES (" + id + ", '" + name + "', '000" + id + "', 'USER', CURRENT_TIMESTAMP,"
				+ " false, '01:00:00', '08:00:00')");
	}

	private void insertDevice(long id, long memberId, String token) {
		exec("INSERT INTO member_device (id, member_id, fcm_token, platform, active,"
				+ " last_registered_at, created_at, updated_at)"
				+ " VALUES (" + id + ", " + memberId + ", '" + token + "', 'ANDROID', true,"
				+ " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
	}

	private void insertMatchSubscription(
			long id, long memberId, boolean liveEvent, boolean kill,
			boolean baron, boolean dragon, boolean tower, boolean inhibitor) {
		exec("INSERT INTO member_match_subscription"
				+ " (id, member_id, match_id, set_start_enabled, set_end_enabled, live_event_enabled,"
				+ "  kill_enabled, baron_enabled, dragon_enabled, tower_enabled, inhibitor_enabled, created_at)"
				+ " VALUES (" + id + ", " + memberId + ", '" + MATCH_ID + "', true, true, " + liveEvent + ","
				+ " " + kill + ", " + baron + ", " + dragon + ", " + tower + ", " + inhibitor + ","
				+ " CURRENT_TIMESTAMP)");
	}

	private void insertTeamSubscription(
			long id, long memberId, long teamId, boolean liveEvent, boolean kill) {
		exec("INSERT INTO member_team_notification_subscription"
				+ " (id, member_id, team_id, set_start_enabled, set_end_enabled, live_event_enabled,"
				+ "  kill_enabled, baron_enabled, dragon_enabled, tower_enabled, inhibitor_enabled,"
				+ "  created_at, updated_at)"
				+ " VALUES (" + id + ", " + memberId + ", " + teamId + ", true, true, " + liveEvent + ","
				+ " " + kill + ", true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
	}

	private void exec(String sql) {
		em.createNativeQuery(sql).executeUpdate();
	}
}
