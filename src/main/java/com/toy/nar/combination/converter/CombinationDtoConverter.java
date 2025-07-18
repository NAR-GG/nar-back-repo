package com.toy.nar.combination.converter;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.toy.nar.combination.domain.ChampionCombination;
import com.toy.nar.combination.dto.CombinationDetailDto;
import com.toy.nar.combination.dto.CombinationResponseDto;
import com.toy.nar.combination.dto.CombinationStatDto;
import com.toy.nar.game.entity.GameParticipant;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CombinationDtoConverter {

	private final GameDetailConverter gameDetailConverter;

	// 🔥 combinationId 포함 버전
	public CombinationResponseDto toResponseDto(ChampionCombination combination, int rank, String combinationId) {
		return new CombinationResponseDto(
			combinationId,
			rank,
			combination.getChampions(),
			combination.getFrequency(),
			combination.getWinCount(),
			combination.getLossCount(),
			combination.getWinRate(),
			combination.getLatestGameDate(),
			new ArrayList<>(combination.getPatches())
		);
	}

	// 🔥 combinationId 없는 버전 (하위 호환성)
	public CombinationResponseDto toResponseDto(ChampionCombination combination, int rank) {
		return new CombinationResponseDto(
			null,
			rank,
			combination.getChampions(),
			combination.getFrequency(),
			combination.getWinCount(),
			combination.getLossCount(),
			combination.getWinRate(),
			combination.getLatestGameDate(),
			new ArrayList<>(combination.getPatches())
		);
	}

	public CombinationDetailDto toDetailDtoMulti(ChampionCombination combination,
		List<GameParticipant> gameDetails,
		List<String> teamNames,
		List<String> targetChampions) {

		CombinationResponseDto summary = toResponseDto(combination, 1, "");

		List<CombinationDetailDto.GameDetailDto> gameDetailDtos =
			gameDetailConverter.convertToGameDetailsMulti(gameDetails, teamNames, targetChampions);

		return new CombinationDetailDto(summary, gameDetailDtos);
	}

	public CombinationDetailDto toDetailDto(ChampionCombination combination,
		List<GameParticipant> gameParticipants,
		String targetTeamName) {
		CombinationResponseDto summary = toResponseDto(combination, 0);
		List<CombinationDetailDto.GameDetailDto> gameDetails =
			gameDetailConverter.convertToGameDetails(
				gameParticipants,
				targetTeamName,
				combination.getChampions()
			);

		return new CombinationDetailDto(summary, gameDetails);
	}

	public CombinationStatDto toStatDto(ChampionCombination combination) {
		return new CombinationStatDto(
			combination.getChampions(),
			combination.getFrequency()
		);
	}
}
