package com.toy.nar.app.notice.dto;

import com.toy.nar.domain.notice.entity.Notice;
import java.time.LocalDateTime;

/** 앱 공지 응답. 작성자는 앱이 "관리자" 고정으로 표기하므로 내려주지 않는다. */
public record NoticeResponse(
        Long id,
        String title,
        String content,
        boolean pinned,
        LocalDateTime publishedAt
) {
    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isPinned(),
                notice.getPublishedAt());
    }
}
