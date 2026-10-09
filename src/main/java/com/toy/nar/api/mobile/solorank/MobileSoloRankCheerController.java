package com.toy.nar.api.mobile.solorank;

import com.toy.nar.app.mobile.solorank.SoloRankCheerService;
import com.toy.nar.app.mobile.solorank.dto.SoloRankCheerRequest;
import com.toy.nar.app.mobile.solorank.dto.SoloRankCheerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 선수 구독", description = "홈 솔랭 카드 응원")
@RestController
@RequestMapping("/api/mobile/solo-rank/players")
@RequiredArgsConstructor
public class MobileSoloRankCheerController {

	private final SoloRankCheerService cheerService;

	@Operation(summary = "솔랭 응원 보내기",
			description = "지금 솔랭 중인 선수에게 응원을 보냅니다. 앱이 짧은 시간의 탭을 모아 count 로 한 번에 보냅니다(1~30). "
					+ "솔랭 중이 아니면 409.")
	@SecurityRequirement(name = "bearerAuth")
	@PostMapping("/{playerId}/cheers")
	public ResponseEntity<SoloRankCheerResponse> cheer(@AuthenticationPrincipal Long memberId,
			@PathVariable Long playerId, @RequestBody SoloRankCheerRequest request) {
		return ResponseEntity.ok(cheerService.cheer(memberId, playerId, request.count()));
	}
}
