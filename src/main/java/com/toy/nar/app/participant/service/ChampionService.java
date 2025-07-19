package com.toy.nar.app.participant.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.participant.dto.ChampionDto;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.repository.ChampionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChampionService {

	private final ChampionRepository championRepository;

	public List<ChampionDto> getAllChampions() {
		// findAll()을 그대로 사용
		List<Champion> champions = championRepository.findAllByOrderByChampionNameKrAsc();

		return champions.stream()
			.map(this::convertToDto)
			.toList();
	}

	private ChampionDto convertToDto(Champion champion) {
		return new ChampionDto(
			champion.getId(),
			champion.getChampionNameKr(),
			champion.getChampionNameEn(),
			champion.getImageUrl()
		);
	}
}
