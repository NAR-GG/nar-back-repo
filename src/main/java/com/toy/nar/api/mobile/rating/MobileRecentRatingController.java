package com.toy.nar.api.mobile.rating;

import com.toy.nar.app.mobile.rating.MobileLivePlayerRatingService;
import com.toy.nar.app.mobile.rating.dto.RecentRatingListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 선수 평점", description = "모바일 라이브 경기 선수 평점 및 한줄평 API")
@RestController
@RequestMapping("/api/mobile/ratings")
@RequiredArgsConstructor
public class MobileRecentRatingController {

	private final MobileLivePlayerRatingService ratingService;

	@Operation(summary = "최근 한줄평 목록 조회",
			description = "모든 경기의 평가 중 한줄평이 달린 것만 최신순으로 조회합니다. 로그인이면 차단한 회원의 평가를 뺍니다.")
	@GetMapping("/recent")
	public ResponseEntity<RecentRatingListResponse> getRecent(
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(ratingService.getRecentWithComment(cursor, size, memberId));
	}
}
