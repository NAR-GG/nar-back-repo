package com.toy.nar.api.mobile.community;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.community.dto.CommunityDtos.BlockCreateRequest;
import com.toy.nar.app.community.dto.CommunityDtos.ReportCreateRequest;
import com.toy.nar.app.community.service.CommunityModerationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/mobile/community")
@RequiredArgsConstructor
public class MobileCommunityModerationController {

	private final CommunityModerationService moderationService;

	@PostMapping("/reports")
	public ResponseEntity<Void> report(
			@RequestBody ReportCreateRequest request,
			@AuthenticationPrincipal Long memberId) {
		moderationService.report(memberId, request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/blocks")
	public ResponseEntity<Void> block(
			@RequestBody BlockCreateRequest request,
			@AuthenticationPrincipal Long memberId) {
		moderationService.block(memberId, request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/blocks/{blockedMemberId}")
	public ResponseEntity<Void> unblock(
			@PathVariable long blockedMemberId,
			@AuthenticationPrincipal Long memberId) {
		moderationService.unblock(memberId, blockedMemberId);
		return ResponseEntity.noContent().build();
	}
}
