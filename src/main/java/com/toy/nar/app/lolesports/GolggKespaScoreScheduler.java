package com.toy.nar.app.lolesports;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.schedule.CacheEvictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * KeSPA Cup 스코어를 gol.gg 에서 주기적으로 백필한다.
 *
 * <p>gol.gg 는 경기 종료 후 스코어를 올리지만 "언제 끝났는지" 알림이 없다. 그래서 경기가
 * 진행 중일 법한 시간대(시작 최대 6시간 전~30분 후)에 KESPA 미완료 매치가 있으면 폴링해,
 * 종료 시각을 몰라도 다음 사이클에서 완료 스코어를 줍는다. 백필은 멱등이라 반복 호출이 안전하다.</p>
 *
 * <p>ponytail: 진행중 매치가 없으면 gol.gg fetch 자체를 건너뛴다 — 대회 없는 날엔 안 돌고
 * 스크랩 부하도 없다. 날짜 하드코딩 대신 DB 일정으로 창을 판단한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GolggKespaScoreScheduler {

	private static final String KESPA = "KESPA";

	private final GolggKespaScoreBackfillService golggKespaScoreBackfillService;
	private final LeagueMatchRepository leagueMatchRepository;
	private final CacheEvictionService cacheEvictionService;

	@Value("${lolesports.kespa.golgg-enabled:true}")
	private boolean enabled;

	/** 기본 5분 간격. 진행중 매치가 있을 때만 실제 스크랩. */
	@Scheduled(fixedDelayString = "${lolesports.kespa.golgg-poll-ms:300000}")
	public void poll() {
		if (!enabled) {
			return;
		}
		LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
		List<LeagueMatch> window = leagueMatchRepository.findByLeagueNameAndDateRange(
				KESPA, nowUtc.minusHours(6), nowUtc.plusMinutes(30));
		boolean anyOngoing = window.stream()
				.anyMatch(m -> !"completed".equalsIgnoreCase(m.getState()));
		if (!anyOngoing) {
			return; // 진행중일 법한 KESPA 매치 없음 — gol.gg 조회 생략
		}
		int updated = golggKespaScoreBackfillService.backfill();
		if (updated > 0) {
			cacheEvictionService.evictScheduleCaches();
			log.info("gol.gg KeSPA 스케줄 백필: {}건 반영", updated);
		}
	}
}
