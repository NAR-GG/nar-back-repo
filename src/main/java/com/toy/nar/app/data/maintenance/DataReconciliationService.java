package com.toy.nar.app.data.maintenance;

import com.toy.nar.domain.game.entity.LeagueTeam;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.LeagueRepository;
import com.toy.nar.domain.game.repository.LeagueTeamRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataReconciliationService {

	private final GameParticipantRepository gameParticipantRepository;
	private final LeagueTeamRepository leagueTeamRepository;
	private final LeagueRepository leagueRepository;
	private final TeamRepository teamRepository;

	// 결과를 담을 DTO
	public record ReconciliationResult(long requiredPairs, long existingPairs, long addedPairs) {}

	// (리그 ID, 팀 ID) 쌍을 표현하기 위한 private record
	private record LeagueTeamPair(Long leagueId, Long teamId) {}

	/**
	 * 역할: DB 전체를 스캔하여 누락된 LeagueTeam 관계를 찾아 추가합니다.
	 */
	@Transactional
	public ReconciliationResult reconcileLeagueTeams() {
		log.info("🕵️ Starting LeagueTeam reconciliation...");

		// 1. GameParticipant 테이블에서 필요한 모든 (리그, 팀) 관계를 조회 ("정답" 목록)
		List<Object[]> requiredPairsRaw = gameParticipantRepository.findAllDistinctLeagueTeamPairs();
		Set<LeagueTeamPair> requiredPairs = requiredPairsRaw.stream()
			.map(pair -> new LeagueTeamPair((Long) pair[0], (Long) pair[1]))
			.collect(Collectors.toSet());
		log.info(" -> Found {} required League-Team pairs from game data.", requiredPairs.size());

		// 2. LeagueTeam 테이블에서 현재 모든 관계를 조회
		List<Object[]> existingPairsRaw = leagueTeamRepository.findAllLeagueTeamPairs();
		Set<LeagueTeamPair> existingPairs = existingPairsRaw.stream()
			.map(pair -> new LeagueTeamPair((Long) pair[0], (Long) pair[1]))
			.collect(Collectors.toSet());
		log.info(" -> Found {} existing League-Team pairs in the join table.", existingPairs.size());

		// 3. "정답" 목록에서 "현재" 목록을 빼서, 누락된 관계만 필터링
		Set<LeagueTeamPair> missingPairs = requiredPairs.stream()
			.filter(pair -> !existingPairs.contains(pair))
			.collect(Collectors.toSet());

		if (missingPairs.isEmpty()) {
			log.info("✅ No missing League-Team pairs found. Reconciliation complete.");
			return new ReconciliationResult(requiredPairs.size(), existingPairs.size(), 0);
		}

		log.warn(" -> Found {} missing League-Team pairs to be added.", missingPairs.size());

		// 4. 누락된 관계를 DB에 새로 저장
		List<LeagueTeam> leagueTeamsToSave = missingPairs.stream()
			.map(pair -> LeagueTeam.builder()
				// getReferenceById는 실제 DB 조회를 지연시켜 성능에 유리합니다.
				.league(leagueRepository.getReferenceById(pair.leagueId()))
				.team(teamRepository.getReferenceById(pair.teamId()))
				.build())
			.toList();

		leagueTeamRepository.saveAll(leagueTeamsToSave);
		log.warn("✅ Successfully added {} new League-Team associations.", leagueTeamsToSave.size());

		return new ReconciliationResult(requiredPairs.size(), existingPairs.size(), leagueTeamsToSave.size());
	}
}