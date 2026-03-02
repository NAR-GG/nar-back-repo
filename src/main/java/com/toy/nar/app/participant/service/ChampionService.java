package com.toy.nar.app.participant.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import com.toy.nar.app.participant.dto.ChampionDto;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.repository.ChampionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChampionService {

	private static final String DDRAGON_LOADING_URL_FORMAT = "https://ddragon.leagueoflegends.com/cdn/img/champion/loading/%s_0.jpg";

	private final ChampionRepository championRepository;

	public List<ChampionDto> getAllChampions() {
		// findAll()을 그대로 사용
		List<Champion> champions = championRepository.findAllByOrderByChampionNameKrAsc();

		return champions.stream()
			.map(this::convertToDto)
			.toList();
	}

	public String generateChampionsEtag(List<ChampionDto> champions) {
		StringBuilder payload = new StringBuilder(champions.size() * 96);
		for (ChampionDto champion : champions) {
			payload.append(champion.id()).append('|')
				.append(safe(champion.championNameKr())).append('|')
				.append(safe(champion.championNameEn())).append('|')
				.append(safe(champion.imageUrl())).append('|')
				.append(safe(champion.loadingImageUrl()))
				.append('\n');
		}

		String hash = DigestUtils.md5DigestAsHex(payload.toString().getBytes(StandardCharsets.UTF_8));
		return "\"" + hash + "\"";
	}

	@Transactional
	public void updateChampionLoadingImage(Long championId, String imageUrl) {
		Champion champion = championRepository.findById(championId)
				.orElseThrow(() -> new IllegalArgumentException("Champion not found: " + championId));
		champion.updateLoadingImageUrl(imageUrl);
	}

	@Transactional
	public int updateAllChampionLoadingImages(boolean overwrite) {
		List<Champion> champions = championRepository.findAll();
		int updatedCount = 0;

		for (Champion champion : champions) {
			if (!overwrite && hasText(champion.getLoadingImageUrl())) {
				continue;
			}

			String loadingImageUrl = buildLoadingImageUrl(champion);
			if (!hasText(loadingImageUrl)) {
				continue;
			}
			if (loadingImageUrl.equals(champion.getLoadingImageUrl())) {
				continue;
			}

			champion.updateLoadingImageUrl(loadingImageUrl);
			updatedCount++;
		}

		return updatedCount;
	}

	private ChampionDto convertToDto(Champion champion) {
		return new ChampionDto(
			champion.getId(),
			champion.getChampionNameKr(),
			champion.getChampionNameEn(),
			champion.getImageUrl(),
			champion.getLoadingImageUrl()
		);
	}

	private String buildLoadingImageUrl(Champion champion) {
		String championKey = extractChampionKeyFromImageUrl(champion.getImageUrl());
		if (!hasText(championKey)) {
			championKey = normalizeChampionKey(champion.getChampionNameEn());
		}
		if (!hasText(championKey)) {
			return null;
		}
		return String.format(DDRAGON_LOADING_URL_FORMAT, championKey);
	}

	private String extractChampionKeyFromImageUrl(String championImageUrl) {
		if (!hasText(championImageUrl)) {
			return null;
		}
		int slashIdx = championImageUrl.lastIndexOf('/');
		String fileName = slashIdx >= 0 ? championImageUrl.substring(slashIdx + 1) : championImageUrl;
		int dotIdx = fileName.lastIndexOf('.');
		return dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
	}

	private String normalizeChampionKey(String championNameEn) {
		if (!hasText(championNameEn)) {
			return null;
		}
		return championNameEn
				.trim()
				.replaceAll("[^A-Za-z0-9]", "");
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}
}
