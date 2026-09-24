package com.toy.nar.domain.participant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 추적 선수가 시작한 솔로 랭크 게임 한 건의 이력.
 *
 * <p>라이브 모니터({@code PlayerSoloRankMonitorService})가 spectator(현재 게임) API로 새 솔랭
 * 게임을 감지한 시점에 적재한다. 구독 여부와 무관하게 모든 추적 선수에 대해 쌓이며,
 * 선수 카드의 "최근 솔랭 / 챔프 폭" 표시에 쓴다.
 *
 * <p>승패·KDA 등 결과는 spectator API에 없어서, 게임이 끝난 뒤 match-v5 로 채운다(V88).
 */
@Entity
@Table(name = "player_solo_rank_game", uniqueConstraints = {
		@UniqueConstraint(
				name = "uk_player_solo_rank_game",
				columnNames = {"player_id", "game_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerSoloRankGame {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	@Column(name = "game_id", nullable = false, length = 64)
	private String gameId;

	/** 시작 시 픽한 챔피언. 해석 실패 시 null. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "champion_id")
	private Champion champion;

	/** 모니터가 게임을 감지한 시각(실제 게임 시작 시각과 다소 차이 있을 수 있음). */
	@Column(name = "detected_at", nullable = false)
	private LocalDateTime detectedAt;

	/** 종료 알림을 낸 시각. null 이면 아직 안 냈다는 뜻이라 재조회 게이트로 쓴다. */
	@Column(name = "end_notified_at")
	private LocalDateTime endNotifiedAt;

	/** 실제 게임 시작(spectator gameStartTime, 끝나면 match-v5 값으로 덮는다). 로딩 화면이면 아직 null. */
	@Column(name = "game_started_at")
	private LocalDateTime gameStartedAt;

	// 아래는 match-v5 결과. 결과를 못 받은 게임(재기동으로 대기열 유실·조회 포기)은 null 로 남는다.
	@Column(name = "game_ended_at")
	private LocalDateTime gameEndedAt;

	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	@Column(name = "win")
	private Boolean win;

	@Column(name = "kills")
	private Integer kills;

	@Column(name = "deaths")
	private Integer deaths;

	@Column(name = "assists")
	private Integer assists;

	public PlayerSoloRankGame(Player player, String gameId, Champion champion, LocalDateTime detectedAt) {
		this.player = player;
		this.gameId = gameId;
		this.champion = champion;
		this.detectedAt = detectedAt;
	}
}
