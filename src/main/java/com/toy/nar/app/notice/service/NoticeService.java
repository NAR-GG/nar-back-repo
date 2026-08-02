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
        return noticeRepository.findByPublishedAtIsNotNullOrderByPinnedDescPublishedAtDesc(pageable);
    }

    /** 앱 캘린더 띠배너 공지 목록 (최신 발행순, 최대 5건). 앱이 안 닫은 첫 건을 띄운다. */
    @Transactional(readOnly = true)
    public List<Notice> promoted() {
        return noticeRepository
                .findByPublishedAtIsNotNullAndPromoteUntilAfterOrderByPublishedAtDesc(
                        LocalDateTime.now(), PageRequest.of(0, 5));
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
        Notice notice = new Notice(title, content, pinned, toEndOfDay(promoteUntil));
        notice.changePublished(published);
        return noticeRepository.save(notice);
    }

    @Transactional
    public Notice update(Long id, String title, String content, boolean pinned,
                         LocalDate promoteUntil, boolean published) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTICE_NOT_FOUND));
        notice.update(title, content, pinned, toEndOfDay(promoteUntil));
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
        if (!noticeRepository.existsById(id)) {
            throw new CustomException(ErrorCode.NOTICE_NOT_FOUND);
        }
        noticeRepository.deleteById(id);
    }

    /** 배너 종료 '일' → 그날 23:59:59 까지 노출. 백오피스가 날짜만 보낸다. */
    private static LocalDateTime toEndOfDay(LocalDate date) {
        return date == null ? null : date.atTime(23, 59, 59);
    }
}
