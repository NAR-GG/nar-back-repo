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

	private final com.toy.nar.app.image.ImageCdn imageCdn;
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
		champion.updateLoadingImageUrl(imageCdn.splash(imageUrl));
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

	private String safe(String value) {
		return value == null ? "" : value;
	}
}
