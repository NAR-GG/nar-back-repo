package com.toy.nar.domain.participant.entity;

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
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "team_external_identity", uniqueConstraints = {
        @UniqueConstraint(name = "uk_team_external_identity_source_external_team", columnNames = { "source",
                "external_team_id" })
}, indexes = {
        @Index(name = "idx_team_external_identity_team_id", columnList = "team_id"),
        @Index(name = "idx_team_external_identity_source_team_id", columnList = "source, team_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamExternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "external_team_id", nullable = false, length = 128)
    private String externalTeamId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "external_name_raw", length = 255)
    private String externalNameRaw;

    @Column(name = "matched_by", length = 50)
    private String matchedBy;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public TeamExternalIdentity(
            String source,
            String externalTeamId,
            Team team,
            String externalNameRaw,
            String matchedBy,
            BigDecimal confidence) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.externalTeamId = Objects.requireNonNull(externalTeamId, "externalTeamId must not be null");
        this.team = Objects.requireNonNull(team, "team must not be null");
        this.externalNameRaw = externalNameRaw;
        this.matchedBy = matchedBy;
        this.confidence = confidence;
    }

    public void updateMatchMetadata(String externalNameRaw, String matchedBy, BigDecimal confidence) {
        this.externalNameRaw = externalNameRaw;
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
