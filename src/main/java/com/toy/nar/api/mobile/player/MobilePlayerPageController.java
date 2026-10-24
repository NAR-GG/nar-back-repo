package com.toy.nar.api.mobile.player;

import com.toy.nar.app.mobile.player.MobilePlayerPageService;
import com.toy.nar.app.mobile.player.dto.PlayerPageResponse;
import com.toy.nar.app.mobile.player.dto.PlayerSoloRankResponse;
import com.toy.nar.app.mobile.player.dto.PlayerWorldsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 선수 페이지", description = "홈 솔랭 카드에서 들어가는 선수 페이지 API. 비회원도 부를 수 있다")
@RestController
@RequestMapping("/api/mobile/players/{playerId}")
@RequiredArgsConstructor
public class MobilePlayerPageController {

	private final MobilePlayerPageService playerPageService;

	@Operation(summary = "선수 페이지 상단",
			description = "프로필, 지금 솔랭 중인 판(응원 포함), 마지막 솔랭, 소속 팀 다음 경기, 구독자·7일 응원·월즈 팬 평점. "
					+ "로그인하면 구독 여부와 내 응원 수가 실린다.")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping
	public ResponseEntity<PlayerPageResponse> getPage(@PathVariable Long playerId,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(playerPageService.page(playerId, memberId));
	}

	@Operation(summary = "선수 페이지 월즈 탭", description = "이번 월즈 지표(포지션 비교)·세트별 기록·챔피언·팬 평점.")
	@GetMapping("/worlds")
	public ResponseEntity<PlayerWorldsResponse> getWorlds(@PathVariable Long playerId,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(playerPageService.worlds(playerId, memberId));
	}

	@Operation(summary = "선수 페이지 한줄평 전체", description = "이번 월즈 한줄평 최신순 페이지. size 는 1~50.")
	@GetMapping("/worlds/reviews")
	public ResponseEntity<PlayerWorldsResponse.ReviewPage> getReviews(@PathVariable Long playerId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(playerPageService.reviews(playerId, memberId, Math.min(Math.max(page, 0), 10_000),
				Math.min(Math.max(size, 1), 50)));
	}

	@Operation(summary = "선수 페이지 솔랭 탭", description = "추적 계정, 최근 20판 요약·모스트·판별 기록(응원 수 포함).")
	@GetMapping("/solo-rank")
	public ResponseEntity<PlayerSoloRankResponse> getSoloRank(@PathVariable Long playerId) {
		return ResponseEntity.ok(playerPageService.soloRank(playerId));
	}
}
