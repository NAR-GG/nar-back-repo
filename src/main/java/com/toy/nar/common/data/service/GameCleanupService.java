package com.toy.nar.common.data.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.common.data.GameStatusAnalyzer;
import com.toy.nar.common.data.dto.CleanupResult;
import com.toy.nar.game.entity.Game;
import com.toy.nar.game.repository.GameParticipantRepository;
import com.toy.nar.game.repository.GameRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameCleanupService {

	private final GameStatusAnalyzer gameAnalyzer;
	private final GameParticipantRepository gameParticipantRepository;
	private final GameRepository gameRepository;

	@Transactional
	public CleanupResult deleteIncompleteGames() {
		log.info("🗑️ Starting deletion of irreparable incomplete games...");

		// 현재 불완전한 게임 조회
		GameStatusAnalyzer.GameStatusReport report = gameAnalyzer.analyzeGameStatus();

		if (report.getIncompleteGames() == 0) {
			return CleanupResult.noGamesToDelete();
		}

		// 불완전한 게임들 삭제
		int deletedCount = 0;
		List<String> deletedGameOriginIds = new ArrayList<>();

		for (Long gameId : report.getIncompleteGameIds()) {
			// gameOriginId 조회 (로깅용)
			Optional<Game> gameOpt = gameRepository.findById(gameId);
			if (gameOpt.isPresent()) {
				deletedGameOriginIds.add(gameOpt.get().getGameOriginId());
			}

			// 참가자 데이터 삭제
			gameParticipantRepository.deleteByGameId(gameId);
			// 게임 데이터 삭제
			gameRepository.deleteById(gameId);
			deletedCount++;
		}

		log.info("✅ Deleted {} incomplete games: {}", deletedCount, deletedGameOriginIds);

		return CleanupResult.success(deletedCount, deletedGameOriginIds);
	}
}
