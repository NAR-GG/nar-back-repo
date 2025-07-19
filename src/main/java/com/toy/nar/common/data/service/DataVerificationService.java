package com.toy.nar.common.data.service;

import com.toy.nar.game.entity.Game;
import com.toy.nar.game.entity.GameParticipant;
import com.toy.nar.game.entity.League;
import com.toy.nar.game.repository.GameRepository;
import com.toy.nar.game.repository.LeagueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataVerificationService {

	private final LeagueRepository leagueRepository;
	private final GameRepository gameRepository;

	// --- DTOs for Reporting (검증 결과를 담을 record 객체들) ---
	public record VerificationReport(
		long totalLeaguesChecked,
		long okLeagues,
		long failedLeagues,
		List<LeagueVerificationResult> details
	) {}

	public record LeagueVerificationResult(
		String leagueName,
		String status,
		long totalGames,
		long okGames,
		long failedGames,
		List<GameVerificationResult> failedGameDetails
	) {}

	public record GameVerificationResult(
		String gameOriginId,
		String status,
		List<String> errors
	) {}
	// --- End of DTOs ---

	/**
	 * 역할: DB의 모든 게임 관련 데이터 정합성을 검증합니다.
	 * 책임: 각 리그-게임-참가자/밴 관계를 순회하며 규칙을 위반하는 데이터를 찾아 리포트합니다.
	 */
	@Transactional(readOnly = true)
	public VerificationReport verifyAllData() {
		log.info("🕵️‍♂️ Starting full data verification...");
		List<League> allLeagues = leagueRepository.findAll();
		List<LeagueVerificationResult> leagueResults = new ArrayList<>();

		for (League league : allLeagues) {
			leagueResults.add(verifyLeague(league));
		}

		long failedLeagues = leagueResults.stream().filter(r -> "FAILED".equals(r.status())).count();

		VerificationReport report = new VerificationReport(
			allLeagues.size(),
			allLeagues.size() - failedLeagues,
			failedLeagues,
			leagueResults
		);
		log.info("✅ Verification complete. OK Leagues: {}, Failed Leagues: {}", report.okLeagues(), report.failedLeagues());
		return report;
	}

	private LeagueVerificationResult verifyLeague(League league) {
		log.debug("Verifying league: {}", league.getLeagueName());
		// N+1 문제를 방지하기 위해 JOIN FETCH로 게임과 하위 엔티티를 한 번에 조회
		List<Game> gamesInLeague = gameRepository.findAllByLeagueIdWithDetails(league.getId());
		List<GameVerificationResult> failedGames = new ArrayList<>();

		for (Game game : gamesInLeague) {
			List<String> errors = verifyGame(game);
			if (!errors.isEmpty()) {
				failedGames.add(new GameVerificationResult(game.getGameOriginId(), "FAILED", errors));
			}
		}

		boolean isLeagueOk = failedGames.isEmpty();
		return new LeagueVerificationResult(
			league.getLeagueName(),
			isLeagueOk ? "OK" : "FAILED",
			gamesInLeague.size(),
			gamesInLeague.size() - failedGames.size(),
			failedGames.size(),
			failedGames // 실패한 게임의 상세 정보만 포함
		);
	}

	private List<String> verifyGame(Game game) {
		List<String> errors = new ArrayList<>();

		// 1. 참가자 수가 10명인지 검사
		if (game.getParticipants().size() != 10) {
			errors.add(String.format("Participant count is %d, not 10.", game.getParticipants().size()));
		}

		// 2. 밴 정보가 비어있는지 검사 (최소 1개 이상 밴을 가정, 룰에 따라 변경 가능)
		if (CollectionUtils.isEmpty(game.getBans())) {
			errors.add("Ban data is missing.");
		}

		// 3. 각 참가자의 연관 엔티티가 null이 아닌지 검사
		for (GameParticipant p : game.getParticipants()) {
			if (p.getPlayer() == null) errors.add(String.format("Participant ID %d has a null Player.", p.getId()));
			if (p.getTeam() == null) errors.add(String.format("Participant ID %d has a null Team.", p.getId()));
			if (p.getChampion() == null) errors.add(String.format("Participant ID %d has a null Champion.", p.getId()));
		}

		return errors;
	}
}
