package com.toy.nar.domain.member.entity;

import com.toy.nar.domain.participant.entity.Player;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_favorite_player", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"member_id", "player_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberFavoritePlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    /** 선수가 솔랭을 시작했을 때 알림. 기존 구독의 동작이라 기본 ON. */
    @Column(name = "start_enabled", nullable = false)
    private boolean startEnabled = true;

    /**
     * 선수가 솔랭 한 판을 마쳤을 때 알림(승패·KDA 포함). 기본 OFF —
     * 선수당 하루 여러 판이라 켜면 알림이 두 배가 된다.
     */
    @Column(name = "end_enabled", nullable = false)
    private boolean endEnabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    /** null 은 "안 보냈다" 이므로 기존 값을 유지한다(구버전 앱 호환). */
    public void updateToggles(Boolean startEnabled, Boolean endEnabled) {
        if (startEnabled != null) {
            this.startEnabled = startEnabled;
        }
        if (endEnabled != null) {
            this.endEnabled = endEnabled;
        }
    }

    @Builder
    public MemberFavoritePlayer(Member member, Player player) {
        this.member = member;
        this.player = player;
        this.createdAt = java.time.LocalDateTime.now();
    }
}
