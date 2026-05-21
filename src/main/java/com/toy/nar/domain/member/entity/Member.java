package com.toy.nar.domain.member.entity;

import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.entity.Player;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    @Column(name = "favorite_league_name", length = 50)
    private String favoriteLeagueName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_team_id")
    private Team favoriteTeam;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberFavoritePlayer> favoritePlayers = new ArrayList<>();

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

    public void completeOnboarding(String favoriteLeagueName, Team favoriteTeam, Collection<Player> favoritePlayers) {
        this.favoriteLeagueName = favoriteLeagueName;
        this.favoriteTeam = favoriteTeam;
        replaceFavoritePlayers(favoritePlayers);
        this.onboardedAt = LocalDateTime.now();
    }

    private void replaceFavoritePlayers(Collection<Player> players) {
        this.favoritePlayers.clear();
        if (players == null) {
            return;
        }
        players.stream()
                .distinct()
                .map(player -> MemberFavoritePlayer.builder()
                        .member(this)
                        .player(player)
                        .build())
                .forEach(this.favoritePlayers::add);
    }

    public boolean isOnboarded() {
        return this.onboardedAt != null;
    }
}
