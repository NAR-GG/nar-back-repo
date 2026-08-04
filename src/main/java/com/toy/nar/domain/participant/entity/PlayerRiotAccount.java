package com.toy.nar.domain.participant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "player_riot_account", uniqueConstraints = {
		@UniqueConstraint(name = "uk_player_riot_account_player", columnNames = "player_id"),
		@UniqueConstraint(name = "uk_player_riot_account_puuid", columnNames = "puuid")
}, indexes = {
		@Index(name = "idx_player_riot_account_platform_enabled", columnList = "platform, enabled, primary_account"),
		@Index(name = "idx_player_riot_account_last_checked_match_id", columnList = "last_checked_match_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerRiotAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	@Column(name = "riot_id", nullable = false, length = 120)
	private String riotId;

	@Column(name = "game_name", nullable = false, length = 100)
	private String gameName;

	@Column(name = "tag_line", nullable = false, length = 32)
	private String tagLine;

	@Column(name = "platform", nullable = false, length = 20)
	private String platform;

	@Column(name = "puuid", nullable = false, length = 128)
	private String puuid;

	@Column(name = "summoner_id", length = 128)
	private String summonerId;

	@Column(name = "primary_account", nullable = false)
	private boolean primaryAccount;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	@Enumerated(EnumType.STRING)
	@Column(name = "live_status", nullable = false, length = 30)
	private PlayerRiotAccountLiveStatus liveStatus;

	@Column(name = "last_checked_match_id", length = 64)
	private String lastCheckedMatchId;

	@Column(name = "last_alerted_match_id", length = 64)
	private String lastAlertedMatchId;

	@Column(name = "last_match_checked_at")
	private LocalDateTime lastMatchCheckedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Builder
	public PlayerRiotAccount(
			Player player,
			String riotId,
			String gameName,
			String tagLine,
			String platform,
			String puuid,
			String summonerId,
			boolean primaryAccount,
			boolean enabled,
			PlayerRiotAccountLiveStatus liveStatus,
			String lastCheckedMatchId,
			String lastAlertedMatchId,
			LocalDateTime lastMatchCheckedAt) {
		this.player = Objects.requireNonNull(player, "player must not be null");
		this.riotId = Objects.requireNonNull(riotId, "riotId must not be null");
		this.gameName = Objects.requireNonNull(gameName, "gameName must not be null");
		this.tagLine = Objects.requireNonNull(tagLine, "tagLine must not be null");
		this.platform = Objects.requireNonNull(platform, "platform must not be null");
		this.puuid = Objects.requireNonNull(puuid, "puuid must not be null");
		this.summonerId = summonerId;
		this.primaryAccount = primaryAccount;
		this.enabled = enabled;
		this.liveStatus = liveStatus == null ? PlayerRiotAccountLiveStatus.OFFLINE : liveStatus;
		this.lastCheckedMatchId = lastCheckedMatchId;
		this.lastAlertedMatchId = lastAlertedMatchId;
		this.lastMatchCheckedAt = lastMatchCheckedAt;
	}

	public void updateResolvedAccount(
			String riotId,
			String gameName,
			String tagLine,
			String platform,
			String puuid) {
		this.riotId = Objects.requireNonNull(riotId, "riotId must not be null");
		this.gameName = Objects.requireNonNull(gameName, "gameName must not be null");
		this.tagLine = Objects.requireNonNull(tagLine, "tagLine must not be null");
		this.platform = Objects.requireNonNull(platform, "platform must not be null");
		this.puuid = Objects.requireNonNull(puuid, "puuid must not be null");
	}

	public void markPrimaryAccount(boolean primaryAccount) {
		this.primaryAccount = primaryAccount;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean shouldSendAlertFor(String matchId) {
		return matchId != null && !matchId.equals(lastAlertedMatchId);
	}

	public void markNoRecentMatch(LocalDateTime checkedAt) {
		this.liveStatus = PlayerRiotAccountLiveStatus.OFFLINE;
		this.lastMatchCheckedAt = checkedAt;
	}

	public void markRecentOtherQueue(String matchId, LocalDateTime checkedAt) {
		this.liveStatus = PlayerRiotAccountLiveStatus.IN_OTHER_GAME;
		this.lastCheckedMatchId = matchId;
		this.lastMatchCheckedAt = checkedAt;
	}

	public void markRecentRankedSolo(String matchId, LocalDateTime checkedAt) {
		this.liveStatus = PlayerRiotAccountLiveStatus.IN_RANKED_SOLO;
		this.lastCheckedMatchId = matchId;
		this.lastMatchCheckedAt = checkedAt;
	}

	public void markMatchCheckHeartbeat(LocalDateTime checkedAt) {
		this.lastMatchCheckedAt = checkedAt;
	}

	public void markAlertSent(String matchId) {
		this.lastAlertedMatchId = matchId;
	}

	/**
	 * 폴링 스냅샷(detached)의 라이브 상태만 이 엔티티로 옮긴다.
	 *
	 * <p>모니터가 사이클 시작에 읽어둔 엔티티를 그대로 {@code save}(merge)하면 전체 컬럼 UPDATE라
	 * {@code riot_id}·{@code platform}·{@code puuid}까지 스냅샷 시점 값으로 되돌아간다. 그 사이
	 * 백오피스에서 계정을 교체했다면(2026-08-04 Loki: C9loki#kr3/NA1 → 옛 Loki#zxc/KR로 롤백)
	 * 엉뚱한 계정을 폴링해 솔랭 알림이 끊긴다. 그래서 상태 컬럼만 골라 옮긴다.
	 */
	public void copyLiveStateFrom(PlayerRiotAccount snapshot) {
		this.liveStatus = snapshot.liveStatus;
		this.lastCheckedMatchId = snapshot.lastCheckedMatchId;
		this.lastAlertedMatchId = snapshot.lastAlertedMatchId;
		this.lastMatchCheckedAt = snapshot.lastMatchCheckedAt;
	}

	@PrePersist
	public void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (liveStatus == null) {
			liveStatus = PlayerRiotAccountLiveStatus.OFFLINE;
		}
		updatedAt = now;
	}

	@PreUpdate
	public void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
