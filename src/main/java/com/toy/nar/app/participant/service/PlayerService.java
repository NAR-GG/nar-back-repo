package com.toy.nar.app.participant.service;

import com.toy.nar.app.participant.dto.PlayerImageSyncResult;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlayerService {

	private final PlayerRepository playerRepository;

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

	@Transactional	public PlayerImageSyncResult syncLckPlayerImages() {
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
			.failedPlayerNames(new ArrayList<>(failedPlayers)) // 스레드 안전 리스트를 일반 리스트로 변환
			.build();
	}

	private boolean isImageAvailable(String urlString) {
		try {
			java.net.URL url = new java.net.URL(urlString);
			java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
			connection.setRequestMethod("HEAD");
			connection.setConnectTimeout(1000); // 타임아웃 1초로 짧게 설정 (속도 위해)
			connection.setReadTimeout(1000);
			connection.setRequestProperty("User-Agent", "Mozilla/5.0");
			
			int responseCode = connection.getResponseCode();
			return (responseCode == 200);
		} catch (Exception e) {
			return false;
		}
	}
}
