package com.toy.nar.api.v3;

import com.toy.nar.app.analysis.dto.PlayerCardListResponse;
import com.toy.nar.app.analysis.service.PlayerCardService;
import com.toy.nar.app.participant.dto.PlayerImageSyncResult;
import com.toy.nar.app.participant.service.PlayerService;
import com.toy.nar.app.player.PlayerProfileCrawlerService;
import com.toy.nar.app.player.PlayerProfileDto;
import com.toy.nar.app.player.PlayerProfileSyncResult;
import com.toy.nar.app.riot.PlayerRiotAccountSyncService;
import com.toy.nar.app.riot.PlayerSoloRankMonitorService;
import com.toy.nar.app.riot.dto.PlayerRiotAlertCheckRequest;
import com.toy.nar.app.riot.dto.PlayerRiotAlertCheckResult;
import com.toy.nar.app.riot.dto.PlayerRiotAccountSyncResult;
import com.toy.nar.app.riot.dto.PlayerSoloRankMonitorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "1.3 선수 관리", description = "선수 정보 관리 API")
@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

	private final PlayerService playerService;
	private final PlayerProfileCrawlerService playerProfileCrawlerService;
	private final PlayerCardService playerCardService;
	private final PlayerRiotAccountSyncService playerRiotAccountSyncService;
	private final PlayerSoloRankMonitorService playerSoloRankMonitorService;

	@Operation(summary = "선수 이미지 URL 수동 업데이트", description = "특정 선수의 이미지 URL을 수동으로 업데이트합니다.")
	@PostMapping("/{playerId}/image")
	public ResponseEntity<String> updatePlayerImage(
			@PathVariable Long playerId,
			@RequestParam String imageUrl) {

		playerService.updatePlayerImage(playerId, imageUrl);
		return ResponseEntity.ok("Player image updated successfully.");
	}

	@Operation(summary = "LCK 선수 이미지 URL 일괄 동기화", description = "LoL Esports getTeams API의 공식 프로필 사진으로 LCK 선수 이미지를 동기화하고, 매칭 실패한 선수 목록을 반환합니다.")
	@PostMapping("/sync-images")
	public ResponseEntity<PlayerImageSyncResult> syncLckPlayerImages() {
		PlayerImageSyncResult result = playerService.syncLckPlayerImages();
		return ResponseEntity.ok(result);
	}

	@Operation(summary = "전체 선수 이미지 URL 초기화", description = "모든 선수의 이미지 URL을 null로 초기화합니다.")
	@DeleteMapping("/images")
	public ResponseEntity<String> resetAllPlayerImages() {
		int count = playerService.resetAllPlayerImages();
		return ResponseEntity.ok("Reset images for " + count + " players.");
	}

	@Operation(summary = "선수 프로필 크롤링", description = "TrackingThePros에서 선수 프로필 정보(본명, 생년월일, 팀, 포지션 등)를 크롤링합니다.")
	@GetMapping("/profile/{gameName}")
	public PlayerProfileDto getPlayerProfile(
			@Parameter(description = "선수 활동명", example = "Faker") @PathVariable String gameName) {
		return playerProfileCrawlerService.crawlPlayerProfile(gameName);
	}

	@Operation(summary = "LCK 선수 프로필 일괄 동기화", description = "LCK 리그 선수들의 프로필 정보를 TrackingThePros에서 크롤링하여 DB에 저장합니다. 실패한 선수 목록을 반환합니다.")
	@PostMapping("/sync-profiles")
	public ResponseEntity<PlayerProfileSyncResult> syncLckPlayerProfiles() {
		PlayerProfileSyncResult result = playerService.syncLckPlayerProfiles();
		return ResponseEntity.ok(result);
	}

	@Operation(summary = "LCK 선수 주 계정 Riot 식별자 동기화", description = "KR 주 계정의 riotId를 기준으로 puuid를 조회해 추적 테이블에 저장합니다.")
	@PostMapping("/riot/sync-primary-accounts")
	public ResponseEntity<PlayerRiotAccountSyncResult> syncPrimaryRiotAccounts() {
		PlayerRiotAccountSyncResult result = playerRiotAccountSyncService.syncPrimaryAccounts();
		return ResponseEntity.ok(result);
	}

	@Operation(summary = "선수 솔랭 감시 수동 실행", description = "추적 대상 주 계정(KR·EUW·NA 등 플랫폼별)에 대해 spectator-v5 현재 게임 상태를 확인하고 솔랭 시작 알림을 전송합니다.")
	@PostMapping("/riot/poll")
	public ResponseEntity<PlayerSoloRankMonitorResult> pollTrackedPlayers() {
		PlayerSoloRankMonitorResult result = playerSoloRankMonitorService.pollTrackedAccounts();
		return ResponseEntity.ok(result);
	}

	@Operation(summary = "선수 솔랭 알림 수동 체크", description = "관리자용 API입니다. puuid로 spectator-v5 현재 게임을 조회하고, 솔랭이면 실제 디스코드 알림을 즉시 전송합니다.")
	@PostMapping("/riot/manual-alert-check")
	public ResponseEntity<PlayerRiotAlertCheckResult> checkAndSendRiotAlert(
			@Valid @RequestBody PlayerRiotAlertCheckRequest request) {
		PlayerRiotAlertCheckResult result = playerSoloRankMonitorService.checkAndSendAlertByPuuid(
				request.puuid(), request.platform());
		return ResponseEntity.ok(result);
	}

	@Operation(summary = "선수 카드 목록 조회", description = "년도/스플릿/패치/진영 필터로 선수 카드 목록(모스트 챔피언 3개 + 선수 상세)을 조회합니다.")
	@GetMapping("/cards")
	public ResponseEntity<PlayerCardListResponse> getPlayerCards(
			@Parameter(description = "리그명 (기본값: LCK)", example = "LCK") @RequestParam(defaultValue = "LCK") String league,
			@Parameter(description = "연도 (기본값: 2026)", example = "2026") @RequestParam(defaultValue = "2026") Integer year,
			@Parameter(description = "스플릿 (예: Round 3-5)") @RequestParam(required = false) String split,
			@Parameter(description = "패치 (예: 14.1)") @RequestParam(required = false) String patch,
			@Parameter(description = "진영 필터 (ALL, BLUE, RED)", example = "ALL") @RequestParam(defaultValue = "ALL") String side,
			@Parameter(description = "페이지 번호(1-base)", example = "1") @RequestParam(defaultValue = "1") Integer page,
			@Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") Integer size) {

		PlayerCardListResponse response = playerCardService.getPlayerCards(league, year, split, patch, side, page, size);
		return ResponseEntity.ok(response);
	}
}
