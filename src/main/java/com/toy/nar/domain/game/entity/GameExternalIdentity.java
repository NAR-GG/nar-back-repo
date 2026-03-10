package com.toy.nar.domain.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "game_external_identity", uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_external_identity_source_external_game", columnNames = { "source",
                "external_game_id" })
}, indexes = {
        @Index(name = "idx_game_external_identity_game_id", columnList = "game_id"),
        @Index(name = "idx_game_external_identity_source_game_id", columnList = "source, game_id"),
        @Index(name = "idx_game_external_identity_source_match_id", columnList = "source, external_match_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameExternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "external_game_id", nullable = false, length = 128)
    private String externalGameId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "external_match_id", length = 128)
    private String externalMatchId;

    @Column(name = "external_league_name", length = 50)
    private String externalLeagueName;

    @Column(name = "match_date")
    private LocalDate matchDate;

    @Column(name = "game_order")
    private Integer gameOrder;

    @Column(name = "matched_by", length = 50)
    private String matchedBy;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public GameExternalIdentity(
            String source,
            String externalGameId,
            Game game,
            String externalMatchId,
            String externalLeagueName,
            LocalDate matchDate,
            Integer gameOrder,
            String matchedBy,
            BigDecimal confidence) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.externalGameId = Objects.requireNonNull(externalGameId, "externalGameId must not be null");
        this.game = Objects.requireNonNull(game, "game must not be null");
        this.externalMatchId = externalMatchId;
        this.externalLeagueName = externalLeagueName;
        this.matchDate = matchDate;
        this.gameOrder = gameOrder;
        this.matchedBy = matchedBy;
        this.confidence = confidence;
    }

    public void updateMatchMetadata(
            String externalMatchId,
            String externalLeagueName,
            LocalDate matchDate,
            Integer gameOrder,
            String matchedBy,
            BigDecimal confidence) {
        this.externalMatchId = externalMatchId;
        this.externalLeagueName = externalLeagueName;
        this.matchDate = matchDate;
        this.gameOrder = gameOrder;
        this.matchedBy = matchedBy;
        this.confidence = confidence;
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
