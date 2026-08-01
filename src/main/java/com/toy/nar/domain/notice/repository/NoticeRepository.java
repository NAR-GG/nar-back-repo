package com.toy.nar.domain.notice.repository;

import com.toy.nar.domain.notice.entity.Notice;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 앱 공지 목록 — 발행분만, 고정 공지 먼저 그 안에서 최신 발행순. */
    Page<Notice> findByPublishedAtIsNotNullOrderByPinnedDescPublishedAtDesc(Pageable pageable);

    /** 앱 캘린더 띠배너용 — 발행됐고 배너 기간이 남은 것들, 최신 발행순. */
    List<Notice> findByPublishedAtIsNotNullAndPromoteUntilAfterOrderByPublishedAtDesc(
            LocalDateTime now, Pageable pageable);

    /** 백오피스 목록 — 임시저장 포함 전체, 고정 먼저 그 안에서 등록 최신순. */
    Page<Notice> findAllByOrderByPinnedDescCreatedAtDesc(Pageable pageable);
}
