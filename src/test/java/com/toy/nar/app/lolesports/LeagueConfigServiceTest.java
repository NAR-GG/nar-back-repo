package com.toy.nar.app.lolesports;

import com.toy.nar.app.lolesports.repository.LeagueConfig;
import com.toy.nar.app.lolesports.repository.LeagueConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 마이그레이션이 MySQL 전용이라 H2 에선 flyway 를 끄고 엔티티 기준으로 스키마를 만든다.
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
})
class LeagueConfigServiceTest {

	@Autowired
	private LeagueConfigRepository leagueConfigRepository;

	private LeagueConfigService leagueConfigService;

	@BeforeEach
	void setUp() {
		leagueConfigService = new LeagueConfigService(leagueConfigRepository);
		leagueConfigService.seedMissing();
	}

	@Test
	@DisplayName("시드: TARGET_LEAGUES 전체 생성, 알림은 LCK·MSI만 on (종전 prod env 동작 보존)")
	void seedDefaults() {
		List<LeagueConfig> all = leagueConfigService.findAll();
		assertThat(all).hasSameSizeAs(LeagueConstants.TARGET_LEAGUES);
		assertThat(all).allMatch(LeagueConfig::isLiveEnabled);
		assertThat(all).allMatch(LeagueConfig::isSyncEnabled);
		assertThat(all.stream().filter(LeagueConfig::isNotificationEnabled).map(LeagueConfig::getLeagueName))
				.containsExactlyInAnyOrder("LCK", "MSI");
	}

	@Test
	@DisplayName("시드: 이미 있는 행은 덮어쓰지 않는다 (운영자 변경 보존)")
	void seedKeepsExistingRows() {
		leagueConfigService.update("LCK", false, false, false);
		leagueConfigService.seedMissing();
		assertThat(leagueConfigService.liveLeagues()).doesNotContain("LCK");
	}

	@Test
	@DisplayName("update: 토글이 liveLeagues/syncLeagues/isNotificationEnabled 에 반영")
	void updateReflectsInQueries() {
		leagueConfigService.update("LPL", false, true, false);

		assertThat(leagueConfigService.liveLeagues()).doesNotContain("LPL");
		assertThat(leagueConfigService.syncLeagues()).doesNotContain("LPL");
		assertThat(leagueConfigService.isNotificationEnabled("LPL")).isTrue();
		// 대소문자·공백 무관
		assertThat(leagueConfigService.isNotificationEnabled(" lpl ")).isTrue();
	}

	@Test
	@DisplayName("update: 알 수 없는 리그면 IllegalArgumentException")
	void updateUnknownLeague() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> leagueConfigService.update("NOPE", true, true, true));
	}

	@Test
	@DisplayName("isNotificationEnabled: null/빈 리그명은 false")
	void notificationNullSafe() {
		assertThat(leagueConfigService.isNotificationEnabled(null)).isFalse();
		assertThat(leagueConfigService.isNotificationEnabled(" ")).isFalse();
	}
}
