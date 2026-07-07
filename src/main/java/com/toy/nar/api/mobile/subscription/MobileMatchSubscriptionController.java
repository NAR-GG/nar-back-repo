package com.toy.nar.api.mobile.subscription;

import com.toy.nar.api.mobile.subscription.dto.MatchSubscribeRequest;
import com.toy.nar.app.mobile.subscription.MobileMatchSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Mobile. 경기 예약 알림", description = "특정 경기 예약 알림 구독 API. 팀 구독과 별개로 경기 단위로 세트 시작/종료/라이브 이벤트를 받는다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/mobile/me/match-subscriptions")
@RequiredArgsConstructor
public class MobileMatchSubscriptionController {

	private final MobileMatchSubscriptionService subscriptionService;

	@Operation(summary = "내 경기 예약 구독 목록 조회",
			description = "구독 중인 경기 ID 목록. 앱이 경기 리스트에서 벨 상태를 표시하는 데 쓴다.")
	@GetMapping
	public ResponseEntity<List<String>> getSubscriptions(@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(subscriptionService.getSubscribedMatchIds(memberId));
	}

	@Operation(summary = "경기 예약 구독 추가", description = "이미 구독 중이면 멱등하게 통과한다.")
	@PostMapping
	public ResponseEntity<Void> subscribe(
			@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody MatchSubscribeRequest request) {
		subscriptionService.subscribe(memberId, request.matchId());
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "경기 예약 구독 해제")
	@DeleteMapping("/{matchId}")
	public ResponseEntity<Void> unsubscribe(
			@AuthenticationPrincipal Long memberId,
			@Parameter(description = "구독 해제할 경기 ID") @PathVariable String matchId) {
		subscriptionService.unsubscribe(memberId, matchId);
		return ResponseEntity.noContent().build();
	}
}
