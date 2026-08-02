package com.toy.nar.domain.notice.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지사항. 백오피스에서 관리자 1인이 작성하고 앱에는 항상 "관리자" 명의로 노출한다.
 * 카테고리 컬럼 없음 — 구분이 필요한 공지는 제목 말머리([업데이트] 등)로 표현한다.
 *
 * <ul>
 *   <li>{@code publishedAt} null = 임시저장. 앱 목록·배너에 나가지 않는다.</li>
 *   <li>{@code promoteUntil} = 앱 캘린더 상단 띠배너 노출 종료 시각. null 이면 배너 미노출.</li>
 *   <li>본문은 마크다운({@code #}, {@code ##}, {@code -}, 이미지) — 앱 렌더러와 계약.</li>
 * </ul>
 */
@Entity
@Table(name = "notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "promote_until")
    private LocalDateTime promoteUntil;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 조회수. 증가는 NoticeRepository 의 UPDATE 쿼리로만 한다(updatedAt 을 건드리지 않도록). */
    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Notice(String title, String content, boolean pinned, LocalDateTime promoteUntil) {
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.pinned = pinned;
        this.promoteUntil = promoteUntil;
    }

    /** 백오피스 저장 — 제목·본문·고정·배너 종료일을 한 번에 갱신한다. */
    public void update(String title, String content, boolean pinned, LocalDateTime promoteUntil) {
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.pinned = pinned;
        this.promoteUntil = promoteUntil;
    }

    /**
     * 발행 상태 변경. 발행 시각은 최초 발행 때 한 번만 찍고 이후 재발행에도 유지한다
     * (앱 목록 정렬·"NEW" 판단 기준이 흔들리지 않도록).
     */
    public void changePublished(boolean published) {
        if (published) {
            if (publishedAt == null) {
                publishedAt = LocalDateTime.now();
            }
        } else {
            publishedAt = null;
        }
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
