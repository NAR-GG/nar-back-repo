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

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 10)
    private String tag;

    @Column
    private String email;

    @Column(name = "favorite_league_name", length = 50)
    private String favoriteLeagueName;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

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
    public Member(String name, String tag, String email) {
        this.name = name;
        this.tag = tag;
        this.email = email;
        this.createdAt = LocalDateTime.now();
    }

    /** 표시용 닉네임. 이름과 태그를 {@code 이름#태그} 형태로 합성한다. */
    public String getNickname() {
        return name + "#" + tag;
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

    public void updateProfile(String name, String tag, Team favoriteTeam, String profileImageUrl) {
        this.name = name;
        this.tag = tag;
        this.favoriteTeam = favoriteTeam;
        this.profileImageUrl = profileImageUrl;
    }

    public boolean isOnboarded() {
        return this.onboardedAt != null;
    }
}
