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
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role = MemberRole.USER;

    @Column(name = "onboarded_at")
    private LocalDateTime onboardedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled;

    @Column(name = "quiet_start_time", nullable = false)
    private LocalTime quietStartTime = LocalTime.of(1, 0);

    @Column(name = "quiet_end_time", nullable = false)
    private LocalTime quietEndTime = LocalTime.of(8, 0);

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

    /**
     * 즐겨찾기 선수를 목표 집합으로 맞춘다. 이미 있는 항목은 남기고 빠진 것만 지우고 새것만 넣는다.
     *
     * <p>예전엔 전부 clear 한 뒤 다시 add 했다. 그러면 같은 선수를 유지하는 재온보딩에서
     * Hibernate 가 컬렉션 삭제보다 INSERT 를 먼저 flush 해
     * {@code Duplicate entry '<member>-<player>' for key 'uq_member_favorite_player'} 로 500 이 났다
     * (실측 2026-07-29 23:27:55, POST /api/auth/onboarding). 유니크 제약이 있는 컬렉션에서
     * clear-then-add 는 쓸 수 없다.</p>
     */
    private void replaceFavoritePlayers(Collection<Player> players) {
        List<Player> desired = players == null
                ? List.of()
                : players.stream().filter(player -> player != null && player.getId() != null).distinct().toList();

        Set<Long> desiredIds = desired.stream().map(Player::getId).collect(Collectors.toSet());
        this.favoritePlayers.removeIf(favorite -> !desiredIds.contains(favorite.getPlayer().getId()));

        Set<Long> keptIds = this.favoritePlayers.stream()
                .map(favorite -> favorite.getPlayer().getId())
                .collect(Collectors.toSet());
        desired.stream()
                .filter(player -> !keptIds.contains(player.getId()))
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

    /** 알림 잠자기 설정을 갱신한다. 값 검증은 서비스에서 끝낸 뒤 호출한다. */
    public void updateQuietHours(boolean enabled, LocalTime startTime, LocalTime endTime) {
        this.quietHoursEnabled = enabled;
        this.quietStartTime = startTime;
        this.quietEndTime = endTime;
    }
}
