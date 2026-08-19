package com.toy.nar.app.participant.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.api.admin.BackofficeController;
import com.toy.nar.app.riot.PlayerRiotAccountSyncService;
import com.toy.nar.app.riot.RiotPlatform;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

/**
 * 백오피스 선수 수정. LCK 출전 이력이 있는 선수만 허용(서버 강제 — UI 필터만 믿지 않는다).
 * 이미지 수정은 overrideImage로 잠금까지 걸어 자동 동기화 덮어쓰기를 차단한다.
 */
@Service
@RequiredArgsConstructor
public class PlayerAdminService {

	private static final String EDITABLE_LEAGUE = "LCK";

	private final com.toy.nar.app.image.ImageCdn imageCdn;
	private final PlayerRepository playerRepository;
	private final TeamRepository teamRepository;
	private final ObjectMapper objectMapper;
	private final PlayerRiotAccountSyncService playerRiotAccountSyncService;

	@Transactional
	public Player update(Long playerId, String imageUrl, Boolean unlockImage, Long currentTeamId,
			Boolean unlockGameAccounts, List<BackofficeController.GameAccountEntry> gameAccounts) {
		Player player = playerRepository.findWithCurrentTeamById(playerId)
				.orElseThrow(() -> new NoSuchElementException("선수를 찾을 수 없습니다: " + playerId));
		if (!playerRepository.hasLeagueParticipation(playerId, EDITABLE_LEAGUE)) {
			throw new IllegalStateException("LCK 출전 이력이 있는 선수만 수정할 수 있습니다");
		}
		if (Boolean.TRUE.equals(unlockImage)) {
			player.unlockImage();
		} else if (imageUrl != null && !imageUrl.isBlank()) {
			player.overrideImage(imageCdn.player(imageUrl.trim()));
		}
		if (Boolean.TRUE.equals(unlockGameAccounts)) {
			player.unlockGameAccounts();
		} else if (gameAccounts != null) {
			player.overrideGameAccounts(serializeGameAccounts(gameAccounts));
			// Riot 실존 검증 + puuid 즉시 반영. 실패 시 예외 전파 → 트랜잭션 롤백(계정 저장 안 됨).
			playerRiotAccountSyncService.syncPlayerAccountNow(player);
		}
		if (currentTeamId != null) {
			Team team = teamRepository.findById(currentTeamId)
					.orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다: " + currentTeamId));
			player.changeCurrentTeam(team);
		}
		return player;
	}

	// 솔랭 전용 선수 등록: LCK 출전 이력 없이도 생성 가능(update 경로와 달리 참여 검증 안 함).
	// Riot 404 등 예외는 트랜잭션 롤백 → Player 삽입도 취소된다.
	@Transactional
	public Player createSoloRankPlayer(String name, String imageUrl, String riotId, String region) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("선수명은 비울 수 없습니다");
		}
		// 중복 검사와 insert 모두 동일한 정규화 값을 사용해 UNIQUE 제약 회피 오탐을 방지한다.
		String trimmedName = name.trim();
		if (riotId == null || !riotId.matches("^.+#.+$")) {
			throw new IllegalArgumentException("riotId는 '이름#태그' 형식이어야 합니다: " + riotId);
		}
		if (playerRepository.findByName(trimmedName).isPresent()) {
			throw new IllegalStateException("이미 존재하는 선수입니다: " + trimmedName);
		}
		// region 미지정 시 KR. 해외 선수는 EUW/NA 등 지정 → 현재 게임 폴링이 해당 플랫폼으로 라우팅된다.
		String resolvedRegion = region == null || region.isBlank() ? "KR" : region.trim().toUpperCase();
		Player player = playerRepository.save(Player.builder()
				.name(trimmedName)
				.imageUrl(imageUrl == null || imageUrl.isBlank() ? null : imageCdn.player(imageUrl.trim()))
				.build());
		// serializeGameAccounts 재사용: Jackson ObjectMapper가 이스케이프를 보장한다.
		player.overrideGameAccounts(serializeGameAccounts(List.of(
				new BackofficeController.GameAccountEntry(resolvedRegion, riotId, null))));
		playerRiotAccountSyncService.resolveAndSaveInCurrentTransaction(
				player, riotId, RiotPlatform.toPlatform(resolvedRegion));
		return player;
	}

	// 기존 선수(비-LCK 포함)에 솔랭 계정 부착/교체. create와 달리 Player를 새로 만들지 않고 LCK 게이트도 없다.
	// 이미 계정이 있으면 riotId/platform을 교체(예: 해외 이적 선수 KR→EUW). Riot 404면 트랜잭션 롤백.
	@Transactional
	public Player attachSoloRankAccount(Long playerId, String riotId, String region, String imageUrl) {
		// currentTeam을 함께 로딩(PlayerRow.from이 트랜잭션 밖에서 팀을 읽으므로 지연로딩 예외 방지).
		Player player = playerRepository.findWithCurrentTeamById(playerId)
				.orElseThrow(() -> new NoSuchElementException("선수를 찾을 수 없습니다: " + playerId));
		if (riotId == null || !riotId.matches("^.+#.+$")) {
			throw new IllegalArgumentException("riotId는 '이름#태그' 형식이어야 합니다: " + riotId);
		}
		String resolvedRegion = region == null || region.isBlank() ? "KR" : region.trim().toUpperCase();
		player.overrideGameAccounts(serializeGameAccounts(List.of(
				new BackofficeController.GameAccountEntry(resolvedRegion, riotId, null))));
		if (imageUrl != null && !imageUrl.isBlank()) {
			player.setImageUrl(imageCdn.player(imageUrl.trim()));
		}
		playerRiotAccountSyncService.resolveAndSaveInCurrentTransaction(
				player, riotId, RiotPlatform.toPlatform(resolvedRegion));
		return player;
	}

	// 크론 파서(RiotIdParser)가 읽는 형식 유지: [{"region","riotId","tier"}], riotId는 반드시 gameName#tagLine.
	private String serializeGameAccounts(List<BackofficeController.GameAccountEntry> accounts) {
		for (var acc : accounts) {
			if (acc.region() == null || acc.region().isBlank()) {
				throw new IllegalArgumentException("region은 비울 수 없습니다");
			}
			if (acc.riotId() == null || !acc.riotId().matches("^.+#.+$")) {
				throw new IllegalArgumentException("riotId는 '이름#태그' 형식이어야 합니다: " + acc.riotId());
			}
		}
		try {
			return objectMapper.writeValueAsString(accounts);
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new IllegalArgumentException("계정 정보 직렬화 실패", e);
		}
	}
}
