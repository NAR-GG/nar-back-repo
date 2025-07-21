package com.toy.nar.app.analysis.converter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.toy.nar.domain.combination.ChampionCombination;
import com.toy.nar.app.analysis.dto.CombinationDetailDto;
import com.toy.nar.app.analysis.dto.CombinationResponseDto;
import com.toy.nar.app.analysis.dto.CombinationStatDto;
import com.toy.nar.domain.game.entity.GameParticipant;

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

		gameDetailDtos = gameDetailDtos.stream()
			.sorted(Comparator.comparing(CombinationDetailDto.GameDetailDto::gameDate, Comparator.reverseOrder()))  // DESC (최신 먼저)
			.toList();

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
