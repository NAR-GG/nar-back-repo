package com.toy.nar.app.participant.service;

import com.toy.nar.app.participant.dto.PlayerImageSyncResult;
import com.toy.nar.app.player.LolesportsPlayerImageClient;
import com.toy.nar.app.player.PlayerProfileCrawlerService;
import com.toy.nar.app.player.PlayerProfileDto;
import com.toy.nar.app.player.PlayerProfileSyncResult;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

	private final PlayerRepository playerRepository;
	private final PlayerProfileCrawlerService playerProfileCrawlerService;
	private final com.toy.nar.app.image.ImageCdn imageCdn;
	private final LolesportsPlayerImageClient lolesportsPlayerImageClient;
	private final ObjectMapper objectMapper;

	@Transactional
	public void updatePlayerImage(Long playerId, String imageUrl) {
		Player player = playerRepository.findById(playerId)
				.orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));
		player.setImageUrl(imageCdn.player(imageUrl));
	}

	@Transactional
	public void updateAllPlayerImagesBatch(List<Long> playerIds, List<String> imageUrls) {
		if (playerIds.size() != imageUrls.size()) {
			throw new IllegalArgumentException("Size mismatch");
		}
		for (int i = 0; i < playerIds.size(); i++) {
			updatePlayerImage(playerIds.get(i), imageUrls.get(i));
		}
	}

	@Transactional
	public int resetAllPlayerImages() {
		List<Player> players = playerRepository.findAll();
		for (Player player : players) {
			player.setImageUrl(null);
		}
		return players.size();
	}

	/**
	 * LCK 선수 이미지를 LoL Esports getTeams API의 공식 프로필 사진으로 동기화.
	 * API에 이미지가 없는 선수는 기존 이미지를 유지하고 실패 목록으로 보고한다.
	 */
	@Transactional
	public PlayerImageSyncResult syncLckPlayerImages() {
		List<Player> players = playerRepository.findPlayersByLeagueName("LCK");
		Map<String, String> imagesByName = lolesportsPlayerImageClient.fetchPlayerImages();
		List<String> failedPlayers = new ArrayList<>();
		int successCount = 0;

		for (Player player : players) {
			String imageUrl = imagesByName.get(player.getName().trim().toLowerCase(Locale.ROOT));
			if (imageUrl != null) {
				player.setImageUrl(imageCdn.player(imageUrl));
				successCount++;
			} else {
				failedPlayers.add(player.getName());
			}
		}

		return PlayerImageSyncResult.builder()
				.totalTarget(players.size())
				.successCount(successCount)
				.failCount(failedPlayers.size())
				.failedPlayerNames(failedPlayers)
				.build();
	}

	/**
	 * LCK 선수들의 프로필 정보를 TrackingThePros에서 크롤링하여 DB에 저장
	 */
	@Transactional
	public PlayerProfileSyncResult syncLckPlayerProfiles() {
		List<Player> players = playerRepository.findPlayersByLeagueName("LCK");
		List<String> failedPlayers = new ArrayList<>();
		int successCount = 0;

		for (Player player : players) {
			try {
				PlayerProfileDto profile = playerProfileCrawlerService.crawlPlayerProfile(player.getName());

				// 프로필이 유효한지 확인 (최소한 realName이나 birthDate가 있어야 성공으로 간주)
				if (profile.getRealName() != null || profile.getBirthDate() != null) {
					String gameAccountsJson = convertGameAccountsToJson(profile.getGameAccounts());

					player.updateProfile(
							profile.getRealName(),
							profile.getBirthDate(),
							profile.getAge(),
							profile.getRole(),
							gameAccountsJson);
					successCount++;
					log.info("Successfully synced profile for player: {}", player.getName());
				} else {
					failedPlayers.add(player.getName());
					log.warn("No profile data found for player: {}", player.getName());
				}
			} catch (Exception e) {
				failedPlayers.add(player.getName());
				log.error("Failed to sync profile for player: {}", player.getName(), e);
			}
		}

		return PlayerProfileSyncResult.builder()
				.totalCount(players.size())
				.successCount(successCount)
				.failCount(failedPlayers.size())
				.failedPlayers(failedPlayers)
				.build();
	}

	private String convertGameAccountsToJson(List<PlayerProfileDto.GameAccountDto> gameAccounts) {
		if (gameAccounts == null || gameAccounts.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(gameAccounts);
		} catch (Exception e) {
			log.warn("Failed to convert gameAccounts to JSON", e);
			return null;
		}
	}
}
