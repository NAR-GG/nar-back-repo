package com.toy.nar.app.notice.service;

import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.notice.entity.Notice;
import com.toy.nar.domain.notice.repository.NoticeRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지사항 조회(앱)·관리(백오피스) 로직.
 *
 * 배너 종료일은 백오피스에서 날짜(yyyy-MM-dd)로 받고 그날 자정 직전까지 노출한다.
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    /** 앱 공지 목록 — 발행분만, 고정 먼저. */
    @Transactional(readOnly = true)
    public Page<Notice> publishedPage(Pageable pageable) {
        return noticeRepository.findByPublishedAtIsNotNullAndSlugIsNullOrderByPinnedDescPublishedAtDesc(pageable);
    }

    /** 앱 캘린더 띠배너 공지 목록 (최신 발행순, 최대 5건). 앱이 안 닫은 첫 건을 띄운다. */
    @Transactional(readOnly = true)
    public List<Notice> promoted() {
        return noticeRepository
                .findByPublishedAtIsNotNullAndSlugIsNullAndPromoteUntilAfterOrderByPublishedAtDesc(
                        LocalDateTime.now(), PageRequest.of(0, 5));
    }

    /** 앱 전용 페이지(광고 안내 등) 조회 — 발행분만. 없거나 임시저장이면 404. */
    @Transactional(readOnly = true)
    public Notice findPublishedBySlug(String slug) {
        return noticeRepository.findBySlugAndPublishedAtIsNotNull(slug)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTICE_NOT_FOUND));
    }

    /** 백오피스 목록 — 임시저장 포함, 고정 먼저. */
    @Transactional(readOnly = true)
    public Page<Notice> adminPage(Pageable pageable) {
        return noticeRepository.findAllByOrderByPinnedDescCreatedAtDesc(pageable);
    }

    /** 백오피스 단건 조회 (수정 화면). */
    @Transactional(readOnly = true)
    public Notice findById(Long id) {
        return noticeRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTICE_NOT_FOUND));
    }

    @Transactional
    public Notice create(String title, String content, boolean pinned,
                         LocalDate promoteUntil, boolean published) {
        return create(title, content, pinned, promoteUntil, published, null, null);
    }

    /**
     * @param slug      앱 전용 페이지 식별자(영문 소문자·숫자·하이픈). 비면 일반 공지. 생성 때만 정한다.
     * @param actionUrl 하단 고정 버튼 주소(https). 비면 버튼 없음.
     */
    @Transactional
    public Notice create(String title, String content, boolean pinned,
                         LocalDate promoteUntil, boolean published, String slug, String actionUrl) {
        String normalizedSlug = normalizeSlug(slug);
        if (normalizedSlug != null && noticeRepository.existsBySlug(normalizedSlug)) {
            throw new CustomException(ErrorCode.NOTICE_INVALID_SLUG);
        }
        Notice notice = new Notice(title, content, pinned, toEndOfDay(promoteUntil),
                normalizedSlug, normalizeActionUrl(actionUrl));
        notice.changePublished(published);
        return noticeRepository.save(notice);
    }

    @Transactional
    public Notice update(Long id, String title, String content, boolean pinned,
                         LocalDate promoteUntil, boolean published) {
        return update(id, title, content, pinned, promoteUntil, published, null);
    }

    /** actionUrl: null = 그대로 둔다(옛 백오피스 화면이 필드를 안 보내도 지워지지 않게), 빈 문자열 = 버튼 제거. */
    @Transactional
    public Notice update(Long id, String title, String content, boolean pinned,
                         LocalDate promoteUntil, boolean published, String actionUrl) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTICE_NOT_FOUND));
        notice.update(title, content, pinned, toEndOfDay(promoteUntil));
        if (actionUrl != null) {
            notice.changeActionUrl(normalizeActionUrl(actionUrl));
        }
        notice.changePublished(published);
        return notice;
    }

    /** 앱에서 공지 상세를 열었을 때 조회수 +1. 중복 제거 없이 열람 횟수를 그대로 센다. */
    @Transactional
    public void increaseViewCount(Long id) {
        if (noticeRepository.increaseViewCount(id) == 0) {
            throw new CustomException(ErrorCode.NOTICE_NOT_FOUND);
        }
    }

    @Transactional
    public void delete(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTICE_NOT_FOUND));
        // 앱이 slug 로 여는 전용 페이지가 사라지면 메뉴가 404 가 된다 — 내리려면 임시저장으로.
        if (notice.getSlug() != null) {
            throw new CustomException(ErrorCode.NOTICE_PAGE_NOT_DELETABLE);
        }
        noticeRepository.deleteById(id);
    }

    private static String normalizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        String trimmed = slug.trim();
        if (!trimmed.matches("[a-z0-9-]{1,40}")) {
            throw new CustomException(ErrorCode.NOTICE_INVALID_SLUG);
        }
        return trimmed;
    }

    /** 앱이 이 주소를 외부 브라우저로 연다 — https 만 허용한다. 빈 값은 null(버튼 없음). */
    private static String normalizeActionUrl(String actionUrl) {
        if (actionUrl == null || actionUrl.isBlank()) {
            return null;
        }
        String trimmed = actionUrl.trim();
        if (!trimmed.startsWith("https://") || trimmed.length() > 500) {
            throw new CustomException(ErrorCode.NOTICE_INVALID_ACTION_URL);
        }
        return trimmed;
    }

    /** 배너 종료 '일' → 그날 23:59:59 까지 노출. 백오피스가 날짜만 보낸다. */
    private static LocalDateTime toEndOfDay(LocalDate date) {
        return date == null ? null : date.atTime(23, 59, 59);
    }
}
