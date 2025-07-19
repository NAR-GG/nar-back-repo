package com.toy.nar.common.data.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.common.data.GameStatusAnalyzer;
import com.toy.nar.common.data.dto.CleanupResult;
import com.toy.nar.game.entity.Game;
import com.toy.nar.game.repository.BanRepository;
import com.toy.nar.game.repository.GameParticipantRepository;
import com.toy.nar.game.repository.GameRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameCleanupService {

	private final GameStatusAnalyzer gameAnalyzer;
	private final GameRepository gameRepository;

	@Transactional
	public CleanupResult deleteIncompleteGames() {
		log.warn("🗑️ Starting cleanup of all incomplete games using Cascade...");

		// 1. 진단: 삭제할 게임 ID 목록을 가져옵니다.
		GameStatusAnalyzer.GameStatusReport report = gameAnalyzer.analyzeGameStatus();
		Set<Long> incompleteGameIds = report.incompleteGameIds();

		if (incompleteGameIds.isEmpty()) {
			log.info("✅ No incomplete games found to delete. The database is clean.");
			return CleanupResult.noGamesToDelete();
		}

		log.info("Found {} incomplete games to delete. Game IDs: {}", incompleteGameIds.size(), incompleteGameIds);

		// 로깅을 위해 gameOriginId를 미리 조회 (선택적)
		Set<String> gameOriginIds = gameRepository.findGameOriginIdsByIds(incompleteGameIds);

		// [변경] 2. 삭제할 Game 엔티티들을 영속성 컨텍스트로 불러옵니다.
		List<Game> gamesToDelete = gameRepository.findAllById(incompleteGameIds);

		// [변경] 3. Game 엔티티를 직접 삭제합니다.
		// 이 메서드는 @OneToMany(cascade = CascadeType.ALL) 옵션을 트리거하여
		// JPA가 알아서 자식인 Ban과 GameParticipant를 먼저 삭제한 후 Game을 삭제합니다.
		if (!gamesToDelete.isEmpty()) {
			gameRepository.deleteAll(gamesToDelete);
		}

		log.warn("✅ Cleanup complete. {} incomplete games and their children have been deleted.", gamesToDelete.size());
		return CleanupResult.success(gamesToDelete.size(), gameOriginIds.stream().toList());
	}
}
