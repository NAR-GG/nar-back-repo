package com.toy.nar.app.participant.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.api.admin.BackofficeController;
import com.toy.nar.app.riot.PlayerRiotAccountSyncService;
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
			player.overrideImage(imageUrl.trim());
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
	public Player createSoloRankPlayer(String name, String imageUrl, String riotId) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("선수명은 비울 수 없습니다");
		}
		if (riotId == null || !riotId.matches("^.+#.+$")) {
			throw new IllegalArgumentException("riotId는 '이름#태그' 형식이어야 합니다: " + riotId);
		}
		if (playerRepository.findByName(name).isPresent()) {
			throw new IllegalStateException("이미 존재하는 선수입니다: " + name);
		}
		Player player = playerRepository.save(Player.builder()
				.name(name.trim())
				.imageUrl(imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim())
				.build());
		// 크론 파서(RiotIdParser)가 읽는 형식: [{"region":"KR","riotId":"...","tier":null}]
		// objectMapper 없이 직접 구성 — 등록 경로에서 riotId는 이미 형식 검증됨.
		player.overrideGameAccounts(buildSingleKrAccountJson(riotId));
		playerRiotAccountSyncService.resolveAndSaveInCurrentTransaction(player, riotId);
		return player;
	}

	// KR 단일 계정 JSON을 objectMapper 없이 구성 — 솔랭 전용 선수 등록 전용.
	// riotId는 이미 #-포함 형식으로 검증된 값만 들어온다.
	private String buildSingleKrAccountJson(String riotId) {
		return "[{\"region\":\"KR\",\"riotId\":\"" + riotId + "\",\"tier\":null}]";
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
