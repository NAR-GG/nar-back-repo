package com.toy.nar.domain.member.entity;

import com.toy.nar.domain.participant.entity.Team;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_team_id")
    private Team favoriteTeam;

    @Column(name = "onboarded_at")
    private LocalDateTime onboardedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Member(String nickname, String email) {
        this.nickname = nickname;
        this.email = email;
        this.createdAt = LocalDateTime.now();
    }

    public void completeOnboarding(Team favoriteTeam) {
        this.favoriteTeam = favoriteTeam;
        this.onboardedAt = LocalDateTime.now();
    }

    public boolean isOnboarded() {
        return this.onboardedAt != null;
    }
}
