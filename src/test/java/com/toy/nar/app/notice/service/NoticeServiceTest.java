package com.toy.nar.app.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.notice.entity.Notice;
import com.toy.nar.domain.notice.repository.NoticeRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @InjectMocks
    private NoticeService noticeService;

    @Mock
    private NoticeRepository noticeRepository;

    @Test
    @DisplayName("발행 생성: publishedAt 이 찍히고 배너 종료일은 그날 23:59:59")
    void createPublished() {
        when(noticeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notice notice = noticeService.create(
                "[업데이트] 안내", "본문", true, LocalDate.of(2026, 8, 4), true);

        assertThat(notice.getPublishedAt()).isNotNull();
        assertThat(notice.getPromoteUntil())
                .isEqualTo(LocalDateTime.of(2026, 8, 4, 23, 59, 59));
        assertThat(notice.isPinned()).isTrue();
    }

    @Test
    @DisplayName("임시저장 생성: publishedAt null")
    void createDraft() {
        when(noticeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notice notice = noticeService.create("초안", "본문", false, null, false);

        assertThat(notice.getPublishedAt()).isNull();
        assertThat(notice.getPromoteUntil()).isNull();
    }

    @Test
    @DisplayName("재발행해도 최초 발행 시각 유지, 임시저장으로 내리면 null")
    void publishTransitions() {
        Notice notice = new Notice("제목", "본문", false, null);
        notice.changePublished(true);
        LocalDateTime firstPublishedAt = notice.getPublishedAt();
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));

        noticeService.update(1L, "제목", "본문", false, null, true);
        assertThat(notice.getPublishedAt()).isEqualTo(firstPublishedAt);

        noticeService.update(1L, "제목", "본문", false, null, false);
        assertThat(notice.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("없는 공지 수정/삭제는 NOTICE_NOT_FOUND")
    void notFound() {
        when(noticeRepository.findById(99L)).thenReturn(Optional.empty());
        when(noticeRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> noticeService.update(99L, "t", "c", false, null, true))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> noticeService.delete(99L))
                .isInstanceOf(CustomException.class);
    }
}
