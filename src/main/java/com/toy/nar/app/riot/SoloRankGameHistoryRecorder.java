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

	/** 이미 적재된 게임인지 확인. 폴백 경로에서 매치 상세 조회 전 선필터로 쓴다. */
	@Transactional(readOnly = true)
	public boolean exists(Long playerId, String gameId) {
		if (playerId == null || gameId == null || gameId.isBlank()) {
			return false;
		}
		return repository.existsByPlayer_IdAndGameId(playerId, gameId);
	}

	/** Riot 의 epoch 밀리초를 이 테이블의 벽시계(detected_at 과 같은 JVM 시간대)로. 0·null 이면 null. */
	public static LocalDateTime toLocal(Long epochMillis) {
		return epochMillis == null || epochMillis <= 0
				? null
				: LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneId.systemDefault());
	}

	/** spectator 의 실제 시작 시각을 비어 있을 때만 채운다. 실패는 흡수한다(폴링을 깨지 않는다). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void fillStartedAt(Player player, String gameId, Long gameStartTimeMillis) {
		LocalDateTime startedAt = toLocal(gameStartTimeMillis);
		if (startedAt == null || player == null || player.getId() == null || gameId == null) {
			return;
		}
		try {
			repository.fillStartedAt(player.getId(), gameId, startedAt);
		} catch (Exception e) {
			log.warn("Failed to fill solo rank start time playerId={} gameId={}", player.getId(), gameId, e);
		}
	}

	/** match-v5 결과를 이력 행에 싣는다. 실패는 흡수한다(알림 발송을 깨지 않는다). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordResult(Player player, String gameId,
			com.toy.nar.app.riot.dto.RiotMatchResponse.Info info,
			com.toy.nar.app.riot.dto.RiotMatchResponse.Participant tracked) {
		if (player == null || player.getId() == null || gameId == null || info == null) {
			return;
		}
		try {
			repository.recordResult(player.getId(), gameId,
					toLocal(info.gameStartTimestamp()), toLocal(info.gameEndTimestamp()), info.durationSeconds(),
					tracked == null ? null : tracked.win(),
					tracked == null ? null : tracked.kills(),
					tracked == null ? null : tracked.deaths(),
					tracked == null ? null : tracked.assists());
		} catch (Exception e) {
			log.warn("Failed to record solo rank result playerId={} gameId={}", player.getId(), gameId, e);
		}
	}

	/** @return 신규 적재 여부. 이미 존재·실패 시 false — 폴백 경로의 알림 중복 방지 게이트로 쓴다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean record(Player player, String gameId, Champion champion, LocalDateTime detectedAt) {
		if (player == null || player.getId() == null || gameId == null || gameId.isBlank()) {
			return false;
		}
		try {
			if (repository.existsByPlayer_IdAndGameId(player.getId(), gameId)) {
				return false;
			}
			repository.save(new PlayerSoloRankGame(player, gameId, champion, detectedAt));
			return true;
		} catch (DataIntegrityViolationException e) {
			// 동시 폴링으로 인한 (player, gameId) 중복 — 무시.
			return false;
		} catch (Exception e) {
			log.warn("Failed to record solo rank game history playerId={} gameId={}",
					player.getId(), gameId, e);
			return false;
		}
	}
}
