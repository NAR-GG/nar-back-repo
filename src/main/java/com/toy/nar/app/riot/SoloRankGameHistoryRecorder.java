package com.toy.nar.app.riot;

import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;
import com.toy.nar.domain.participant.repository.PlayerSoloRankGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 추적 선수의 새 솔랭 게임을 이력 테이블에 적재한다.
 *
 * <p>모니터 폴링 트랜잭션과 분리({@code REQUIRES_NEW})하고 예외를 흡수해, 적재 실패가
 * 폴링 흐름이나 알림 발송을 깨지 않도록 한다(피드 기록과 동일한 안전 원칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoloRankGameHistoryRecorder {

	private final PlayerSoloRankGameRepository repository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(Player player, String gameId, Champion champion, LocalDateTime detectedAt) {
		if (player == null || player.getId() == null || gameId == null || gameId.isBlank()) {
			return;
		}
		try {
			if (repository.existsByPlayer_IdAndGameId(player.getId(), gameId)) {
				return;
			}
			repository.save(new PlayerSoloRankGame(player, gameId, champion, detectedAt));
		} catch (DataIntegrityViolationException e) {
			// 동시 폴링으로 인한 (player, gameId) 중복 — 무시.
		} catch (Exception e) {
			log.warn("Failed to record solo rank game history playerId={} gameId={}",
					player.getId(), gameId, e);
		}
	}
}
