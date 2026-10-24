package com.toy.nar.api.mobile.notice;

import com.toy.nar.app.notice.dto.NoticeResponse;
import com.toy.nar.app.notice.service.NoticeService;
import com.toy.nar.domain.notice.entity.Notice;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모바일 공지사항 API. 비회원도 보는 공개 API 라 인증이 없다
 * (SecurityConfig 의 /api/** permitAll 에 포함).
 */
@Tag(name = "Mobile. 공지사항", description = "앱 공지 목록·캘린더 띠배너 공지 조회 (인증 불필요)")
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class MobileNoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "공지 목록",
            description = "발행된 공지만, 고정 공지 먼저 그 안에서 최신순. Spring Page 형식. "
                    + "ADMIN 토큰으로 호출하면 임시저장(publishedAt null)도 포함해 내려준다 — 앱 검수용.")
    @GetMapping
    public Page<NoticeResponse> notices(Pageable pageable, Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        Page<Notice> page =
                admin ? noticeService.adminPage(pageable) : noticeService.publishedPage(pageable);
        return page.map(NoticeResponse::from);
    }

    @Operation(summary = "공지 조회수 증가",
            description = "앱에서 공지 상세를 열 때 호출한다. 목록에 본문이 함께 내려가므로 앱이 직접 알려줘야 조회수가 쌓인다. "
                    + "중복 제거 없이 열람 횟수를 센다. 없는 공지면 404.")
    @PostMapping("/{id}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void increaseViewCount(@PathVariable Long id) {
        noticeService.increaseViewCount(id);
    }

    @Operation(summary = "전용 페이지 조회",
            description = "앱이 전용 화면으로 여는 고정 페이지(예: slug=ad-guide 광고 안내). 공지 목록·띠배너에는 나오지 않는다. "
                    + "발행분만, 없거나 임시저장이면 404. actionUrl 이 있으면 앱이 하단 고정 버튼으로 외부 브라우저를 연다.")
    @GetMapping("/by-slug/{slug}")
    public NoticeResponse bySlug(@PathVariable String slug) {
        return NoticeResponse.from(noticeService.findPublishedBySlug(slug));
    }

    @Operation(summary = "띠배너 공지 목록",
            description = "캘린더 상단 띠배너 대상 공지 (최신 발행순, 최대 5건). 앱은 닫지 않은 첫 건을 띄운다. 없으면 빈 배열.")
    // 앱 첫 화면이 진입 즉시 부른다. 경기 시작 버스트에 요청마다 커넥션을 잡지 않게 60초 재사용한다.
    @Cacheable(value = "promotedNotices", key = "'all'", sync = true)
    @GetMapping("/promoted")
    public List<NoticeResponse> promoted() {
        return noticeService.promoted().stream().map(NoticeResponse::from).toList();
    }
}
