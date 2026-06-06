package com.toy.nar.api.mobile.rating;

import com.toy.nar.app.mobile.rating.MobileLivePlayerRatingService;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingDetailResponse;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingListResponse;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 선수 평점", description = "모바일 라이브 경기 선수 평점 및 한줄평 API")
@RestController
@RequestMapping("/api/mobile/live/games/{gameId}")
@RequiredArgsConstructor
public class MobileLivePlayerRatingController {

	private final MobileLivePlayerRatingService ratingService;

	@Operation(summary = "세트 선수 평점 목록 조회")
	@GetMapping("/ratings")
	public ResponseEntity<LivePlayerRatingListResponse> getRatings(
			@PathVariable String gameId,
			@RequestParam(defaultValue = "ALL") String teamSide,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(ratingService.getRatings(gameId, teamSide, memberId));
	}

	@Operation(summary = "선수 평점 상세 및 리뷰 조회")
	@GetMapping("/participants/{participantId}/ratings")
	public ResponseEntity<LivePlayerRatingDetailResponse> getDetail(
			@PathVariable String gameId,
			@PathVariable Integer participantId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(ratingService.getDetail(gameId, participantId, memberId, page, size));
	}

	@Operation(summary = "내 선수 평가 작성 또는 수정")
	@SecurityRequirement(name = "bearerAuth")
	@PutMapping("/participants/{participantId}/my-rating")
	public ResponseEntity<LivePlayerRatingDetailResponse.MyRating> save(
			@PathVariable String gameId,
			@PathVariable Integer participantId,
			@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody LivePlayerRatingRequest request) {
		return ResponseEntity.ok(ratingService.save(gameId, participantId, memberId, request));
	}

	@Operation(summary = "내 선수 평가 삭제")
	@SecurityRequirement(name = "bearerAuth")
	@DeleteMapping("/participants/{participantId}/my-rating")
	public ResponseEntity<Void> delete(
			@PathVariable String gameId,
			@PathVariable Integer participantId,
			@AuthenticationPrincipal Long memberId) {
		ratingService.delete(gameId, participantId, memberId);
		return ResponseEntity.noContent().build();
	}
}
