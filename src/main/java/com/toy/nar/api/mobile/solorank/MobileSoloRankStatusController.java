package com.toy.nar.api.mobile.solorank;

import com.toy.nar.app.mobile.solorank.MobileSoloRankStatusService;
import com.toy.nar.app.mobile.solorank.dto.SoloRankStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 선수 구독", description = "모바일 구독 선수 솔로 랭크 상태 API")
@RestController
@RequestMapping("/api/mobile/me/solo-rank")
@RequiredArgsConstructor
public class MobileSoloRankStatusController {

	private final MobileSoloRankStatusService soloRankStatusService;

	@Operation(summary = "구독 선수 솔랭 상태 조회",
			description = "지금 솔랭 중인 구독 선수와, 최근 24시간 안에 끝낸 선수(선수당 마지막 1판)를 조회합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping
	public ResponseEntity<SoloRankStatusResponse> getStatus(@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(soloRankStatusService.getStatus(memberId));
	}
}
