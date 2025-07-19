package com.toy.nar.common.data.service;

import com.toy.nar.game.repository.*;
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
public class LeagueCleanupService {

	private final LeagueRepository leagueRepository;
	private final GameRepository gameRepository;
	private final BanRepository banRepository;
	private final GameParticipantRepository gameParticipantRepository;
	private final LeagueTeamRepository leagueTeamRepository;

	/**
	 * 역할: 특정 리그와 관련된 모든 데이터를 DB에서 안전하게 삭제합니다.
	 * 책임: 외래 키 제약 조건을 고려하여 올바른 순서로 데이터를 삭제하고, 전체 과정을 트랜잭션으로 관리합니다.
	 * @param leagueName 삭제할 리그의 이름 (e.g., "EWC")
	 */
	@Transactional
	public void deleteLeagueDataSafely(String leagueName) {
		log.warn("🔥 Starting targeted data deletion for league: {}", leagueName);

		// 1. 해당 리그에 속한 모든 게임 ID를 조회합니다.
		List<Long> gameIds = gameRepository.findGameIdsByLeague_LeagueName(leagueName);

		if (!gameIds.isEmpty()) {
			log.info(" -> Found {} games to delete for league '{}'.", gameIds.size(), leagueName);
			Set<Long> gameIdSet = Set.copyOf(gameIds);

			// 2. Game의 자식 테이블 데이터부터 삭제 (Bans, GameParticipants)
			// Bulk Delete로 한 번의 쿼리로 효율적으로 삭제합니다.
			banRepository.deleteByGameIdIn(gameIdSet);
			gameParticipantRepository.deleteByGameIdIn(gameIdSet);
			log.info("   -> Deleted associated bans and participants.");

			// 3. Game 테이블 데이터 삭제
			gameRepository.deleteAllByIdInBatch(gameIds);
			log.info("   -> Deleted associated games.");
		} else {
			log.info(" -> No games found for league '{}'. Skipping game data deletion.", leagueName);
		}

		// 4. League의 다른 자식 테이블 데이터 삭제 (LeagueTeam)
		int deletedLeagueTeams = leagueTeamRepository.deleteByLeague_LeagueName(leagueName);
		log.info(" -> Deleted {} league-team association records.", deletedLeagueTeams);

		// 5. 마지막으로 League 테이블의 데이터 삭제
		int deletedLeagues = leagueRepository.deleteByLeagueName(leagueName);
		log.info(" -> Deleted {} league records.", deletedLeagues);

		log.warn("✅ Successfully cleaned up all data for league: {}", leagueName);
	}
}