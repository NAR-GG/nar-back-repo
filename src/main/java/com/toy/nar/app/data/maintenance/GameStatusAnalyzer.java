package com.toy.nar.app.data.maintenance;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.domain.game.repository.GameRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameStatusAnalyzer {

	private final GameRepository gameRepository;

	@Transactional(readOnly = true) // 읽기 전용 트랜잭션으로 성능 최적화
	public GameStatusReport analyzeGameStatus() {
		log.info("🔍 Analyzing the integrity of all game data in the database...");

		// 1. Native Query를 사용해 불완전한 게임의 ID를 효율적으로 조회
		Set<Long> incompleteGameIds = gameRepository.findIncompleteGameIds();

		// 2. 전체 게임 수 조회
		long totalGames = gameRepository.count();
		long completeGames = totalGames - incompleteGameIds.size();

		GameStatusReport report = new GameStatusReport(
			totalGames,
			completeGames,
			incompleteGameIds.size(),
			incompleteGameIds
		);

		log.info("📊 Game Status Analysis Complete:");
		log.info("   📈 Total games: {}", report.totalGames());
		log.info("   ✅ Complete games (10 participants): {}", report.completeGames());
		log.info("   ❌ Incomplete games (not 10 participants): {}", report.incompleteGames());

		return report;
	}

	// 분석 결과를 담는 불변 객체
	public record GameStatusReport(
		long totalGames,
		long completeGames,
		long incompleteGames,
		Set<Long> incompleteGameIds
	) {}
}