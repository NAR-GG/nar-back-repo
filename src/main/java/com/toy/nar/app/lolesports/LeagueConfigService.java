package com.toy.nar.app.lolesports;

import com.toy.nar.app.lolesports.repository.LeagueConfig;
import com.toy.nar.app.lolesports.repository.LeagueConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 리그별 라이브 수집/디스코드 알림/경기 동기화 토글 조회·변경.
 * 스케줄러(라이브 discovery 60s, 경기 동기화 6h)가 사이클마다 조회한다 — 9행이라 캐시 없이 DB 직조회.
 */
@Service
@RequiredArgsConstructor
public class LeagueConfigService {

	private final LeagueConfigRepository leagueConfigRepository;

	/** 종전 prod env(LOLESPORTS_LIVE_NOTIFICATION_LEAGUES=LCK,MSI) 동작을 보존하는 알림 시드 기본값. */
	private static final java.util.Set<String> DEFAULT_NOTIFICATION_LEAGUES = java.util.Set.of("LCK", "MSI");

	/**
	 * 기동 시 TARGET_LEAGUES 기준 insert-if-missing 시드.
	 * 기본값은 종전 동작 보존: 수집·동기화 전체 on, 디스코드 알림은 LCK·MSI 만 on.
	 */
	@PostConstruct
	void seedMissing() {
		for (String league : LeagueConstants.TARGET_LEAGUES) {
			if (!leagueConfigRepository.existsById(league)) {
				leagueConfigRepository.save(LeagueConfig.builder()
						.leagueName(league)
						.liveEnabled(true)
						.notificationEnabled(DEFAULT_NOTIFICATION_LEAGUES.contains(league))
						.syncEnabled(true)
						.build());
			}
		}
	}

	public List<String> liveLeagues() {
		return enabledLeagues(LeagueConfig::isLiveEnabled);
	}

	public List<String> syncLeagues() {
		return enabledLeagues(LeagueConfig::isSyncEnabled);
	}

	public boolean isNotificationEnabled(String leagueName) {
		if (leagueName == null || leagueName.isBlank()) {
			return false;
		}
		return leagueConfigRepository.findById(leagueName.trim().toUpperCase())
				.map(LeagueConfig::isNotificationEnabled)
				.orElse(false);
	}

	public List<LeagueConfig> findAll() {
		return leagueConfigRepository.findAll().stream()
				.sorted(Comparator.comparing(LeagueConfig::getLeagueName))
				.toList();
	}

	@Transactional
	public LeagueConfig update(String leagueName, boolean liveEnabled, boolean notificationEnabled, boolean syncEnabled) {
		LeagueConfig config = leagueConfigRepository.findById(leagueName.trim().toUpperCase())
				.orElseThrow(() -> new IllegalArgumentException("알 수 없는 리그: " + leagueName));
		config.update(liveEnabled, notificationEnabled, syncEnabled);
		return config;
	}

	private List<String> enabledLeagues(java.util.function.Predicate<LeagueConfig> enabled) {
		return leagueConfigRepository.findAll().stream()
				.filter(enabled)
				.map(LeagueConfig::getLeagueName)
				.sorted()
				.toList();
	}
}
