package com.toy.nar.api.mobile.rating;

import com.toy.nar.app.mobile.rating.MobileLivePlayerRatingService;
import com.toy.nar.app.mobile.rating.dto.MyRatingListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 내 평가", description = "모바일 마이페이지 내 선수 평가 모아보기 API")
@RestController
@RequestMapping("/api/mobile/me/ratings")
@RequiredArgsConstructor
public class MobileMyRatingController {

	private final MobileLivePlayerRatingService ratingService;

	@Operation(summary = "내 선수 평가 전체 목록 조회", description = "로그인한 회원이 작성한 모든 선수 평가를 최신순으로 조회합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping
	public ResponseEntity<MyRatingListResponse> getMyRatings(
			@AuthenticationPrincipal Long memberId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(ratingService.getMyRatings(memberId, page, size));
	}
}
