package com.toy.nar.app.participant.service;

import com.toy.nar.app.participant.dto.PlayerImageSyncResult;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

	private final PlayerRepository playerRepository;
	private final PlayerProfileCrawlerService playerProfileCrawlerService;
	private final ObjectMapper objectMapper;

	@Transactional
	public void updatePlayerImage(Long playerId, String imageUrl) {
		Player player = playerRepository.findById(playerId)
				.orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));
		player.setImageUrl(imageUrl);
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

	@Transactional
	public PlayerImageSyncResult syncLckPlayerImages() {
		List<Player> players = playerRepository.findPlayersByLeagueName("LCK");
		List<String> failedPlayers = Collections.synchronizedList(new ArrayList<>());

		// 1. 유효한 이미지 URL 매핑 정보 수집 (병렬)
		Map<Player, String> validImages = players.parallelStream()
				.map(player -> {
					String name = player.getName();
					String encodedName = name.replace(" ", "%20");
					String imageUrl = "https://images.epromatch.com/lol/player/" + encodedName + ".png";

					if (isImageAvailable(imageUrl)) {
						return new java.util.AbstractMap.SimpleEntry<>(player, imageUrl);
					} else {
						failedPlayers.add(name);
						return null;
					}
				})
				.filter(java.util.Objects::nonNull)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		// 2. DB 업데이트 (순차)
		validImages.forEach(Player::setImageUrl);

		return PlayerImageSyncResult.builder()
				.totalTarget(players.size())
				.successCount(validImages.size())
				.failCount(failedPlayers.size())
				.failedPlayerNames(new ArrayList<>(failedPlayers))
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

	private boolean isImageAvailable(String urlString) {
		try {
			java.net.URL url = new java.net.URL(urlString);
			java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
			connection.setRequestMethod("HEAD");
			connection.setConnectTimeout(1000);
			connection.setReadTimeout(1000);
			connection.setRequestProperty("User-Agent", "Mozilla/5.0");

			int responseCode = connection.getResponseCode();
			return (responseCode == 200);
		} catch (Exception e) {
			return false;
		}
	}
}
