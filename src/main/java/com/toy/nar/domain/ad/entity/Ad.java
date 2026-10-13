package com.toy.nar.domain.ad.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱 광고 배너(이미지 + 이동 주소). {@code placement} 는 앱 자리 키 — 앱과의 계약이다.
 * 노출 기간 {@code startAt}·{@code endAt} 은 null 이면 무제한(서버 로컬 시각, 백오피스는 KST 입력).
 */
@Entity
@Table(name = "ad")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ad {

    public static final Set<String> PLACEMENTS = Set.of("home", "match_list", "match_detail");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "placement", nullable = false, length = 20)
    private String placement;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Ad(String title, String placement, String imageUrl, String linkUrl, int sortOrder,
              boolean active, LocalDateTime startAt, LocalDateTime endAt) {
        update(title, placement, imageUrl, linkUrl, sortOrder, active, startAt, endAt);
    }

    public void update(String title, String placement, String imageUrl, String linkUrl, int sortOrder,
                       boolean active, LocalDateTime startAt, LocalDateTime endAt) {
        if (!PLACEMENTS.contains(placement)) {
            throw new IllegalArgumentException("알 수 없는 노출 위치: " + placement);
        }
        if (linkUrl != null && !linkUrl.isBlank() && !linkUrl.startsWith("https://")) {
            throw new IllegalArgumentException("이동 주소는 https:// 로 시작해야 합니다.");
        }
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 뒤여야 합니다.");
        }
        this.title = Objects.requireNonNull(title);
        this.placement = placement;
        this.imageUrl = Objects.requireNonNull(imageUrl);
        this.linkUrl = (linkUrl == null || linkUrl.isBlank()) ? null : linkUrl;
        this.sortOrder = sortOrder;
        this.active = active;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
