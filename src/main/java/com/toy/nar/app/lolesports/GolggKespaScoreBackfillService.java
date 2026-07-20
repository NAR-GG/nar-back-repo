package com.toy.nar.app.lolesports;

import com.toy.nar.app.lolesports.GolggKespaScoreClient.GameRow;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * gol.gg 에서 긁은 KeSPA Cup 게임 결과를 DB LeagueMatch 의 세트 스코어·상태로 백필한다.
 *
 * <p>lolesports 가 KeSPA 를 completed 로 뒤집지 못하므로(Disney+ 독점, 피드 없음) 이 경로가
 * 유일한 스코어 소스다. 종료-후 보정이라 스케줄러 대신 관리자 트리거로 돌린다.</p>
 *
 * <p>매칭: gol.gg 게임 행을 (정렬된 팀코드쌍 + KST 경기일)로 묶어 세트 승수를 집계하고,
 * 같은 키의 DB 매치에 blue/red 방향을 맞춰 기록한다. Bo1 이면 1게임, BoN 이면 게임 수만큼 누적된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GolggKespaScoreBackfillService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final String KESPA = "KESPA";

	private final GolggKespaScoreClient golggKespaScoreClient;
	private final LeagueMatchRepository leagueMatchRepository;

	@Transactional
	public int backfill() {
		List<GameRow> rows = golggKespaScoreClient.fetchCompletedGames();
		if (rows.isEmpty()) {
			log.info("gol.gg KeSPA 완료 게임 없음 — 백필 건너뜀");
			return 0;
		}
		Map<String, Map<String, Integer>> winsByGroup = aggregateWins(rows);

		List<LeagueMatch> matches = leagueMatchRepository
				.findByLeagueNameOrderByMatchDateDesc(KESPA, Pageable.unpaged());
		LocalDateTime now = LocalDateTime.now();
		int updated = 0;
		for (LeagueMatch m : matches) {
			if (m.getMatchDate() == null || m.getBlueTeamCode() == null || m.getRedTeamCode() == null) {
				continue;
			}
			LocalDate dateKst = m.getMatchDate().atOffset(ZoneOffset.UTC)
					.atZoneSameInstant(KST).toLocalDate();
			Map<String, Integer> wins = winsByGroup.get(
					groupKey(m.getBlueTeamCode(), m.getRedTeamCode(), dateKst));
			if (wins == null) {
				continue;
			}
			int blue = wins.getOrDefault(m.getBlueTeamCode().toUpperCase(), 0);
			int red = wins.getOrDefault(m.getRedTeamCode().toUpperCase(), 0);
			if (blue == 0 && red == 0) {
				continue;
			}
			if (Objects.equals(m.getBlueScore(), blue) && Objects.equals(m.getRedScore(), red)
					&& "completed".equalsIgnoreCase(m.getState())) {
				continue; // 이미 최신
			}
			m.applyExternalScore(blue, red, "completed", now);
			updated++;
		}
		log.info("gol.gg KeSPA 스코어 백필 완료: {}건 업데이트 (gol.gg 게임 {}개)", updated, rows.size());
		return updated;
	}

	/** (정렬 팀코드쌍 @ KST일) → {팀코드: 세트승수}. 게임마다 승자에 +1. */
	private static Map<String, Map<String, Integer>> aggregateWins(List<GameRow> rows) {
		Map<String, Map<String, Integer>> byGroup = new HashMap<>();
		for (GameRow r : rows) {
			String key = groupKey(r.leftCode(), r.rightCode(), r.dateKst());
			Map<String, Integer> wins = byGroup.computeIfAbsent(key, k -> new HashMap<>());
			wins.putIfAbsent(r.leftCode().toUpperCase(), 0);
			wins.putIfAbsent(r.rightCode().toUpperCase(), 0);
			String winner = r.leftScore() > r.rightScore() ? r.leftCode() : r.rightCode();
			wins.merge(winner.toUpperCase(), 1, Integer::sum);
		}
		return byGroup;
	}

	private static String groupKey(String a, String b, LocalDate date) {
		String x = a.toUpperCase();
		String y = b.toUpperCase();
		String pair = x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
		return pair + "@" + date;
	}
}
