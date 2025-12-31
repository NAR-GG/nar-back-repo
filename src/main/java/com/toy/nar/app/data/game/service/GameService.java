package com.toy.nar.app.data.game.service;

import com.toy.nar.app.analysis.dto.MultiCombinationFilterDto;
import com.toy.nar.app.data.game.dto.GameResponseDto;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {

	private final GameRepository gameRepository;
	private final GameParticipantRepository gameParticipantRepository;

	public Page<GameResponseDto> findRecentGames(MultiCombinationFilterDto filter, Pageable pageable) {
		// 1. 필터링된 게임 목록을 페이지네이션하여 조회 (첫 번째 쿼리)
		Page<Game> gamePage = gameRepository.findGamesByFilter(filter, pageable);
		List<Long> gameIds = gamePage.getContent().stream().map(Game::getId).toList();

		if (gameIds.isEmpty()) {
			return Page.empty(pageable);
		}

		// 2. N+1 방지: 조회된 게임들의 참가자 정보를 한 번에 조회 (두 번째 쿼리)
		Map<Long, List<GameParticipant>> participantsByGameId = gameParticipantRepository.findWithDetailsByGameIds(gameIds)
			.stream()
			.collect(Collectors.groupingBy(p -> p.getGame().getId()));

		// 3. Game 엔티티와 참가자 정보를 조합하여 최종 DTO로 변환
		List<GameResponseDto> responseDtos = gamePage.getContent().stream()
			.map(game -> convertToDto(game, participantsByGameId.getOrDefault(game.getId(), List.of())))
			.toList();

		return new PageImpl<>(responseDtos, pageable, gamePage.getTotalElements());
	}

	private GameResponseDto convertToDto(Game game, List<GameParticipant> participants) {
		Map<String, List<GameParticipant>> participantsBySide = participants.stream()
			.collect(Collectors.groupingBy(GameParticipant::getSide));

		GameResponseDto.TeamInGameDto blueTeamDto = createTeamDto(participantsBySide.get("Blue"));
		GameResponseDto.TeamInGameDto redTeamDto = createTeamDto(participantsBySide.get("Red"));

		return new GameResponseDto(
			game.getId(),
			game.getLeague().getLeagueName(),
			game.getPatch(),
			game.getActualGameStartTime(),
			game.getGameLengthSeconds(),
			blueTeamDto,
			redTeamDto
		);
	}

	private GameResponseDto.TeamInGameDto createTeamDto(List<GameParticipant> participants) {
		if (CollectionUtils.isEmpty(participants)) return null;

		GameParticipant first = participants.get(0);

		// 방어 코드: 팀 정보가 누락된 경우
		if (first.getTeam() == null) return null;

		List<GameResponseDto.PlayerInGameDto> playerDtos = participants.stream()
			.map(p -> {
				// 방어 코드: 선수나 챔피언 정보가 누락된 경우 안전하게 처리
				String playerName = (p.getPlayer() != null) ? p.getPlayer().getName() : "Unknown";
				String championName = (p.getChampion() != null) ? p.getChampion().getChampionNameEn() : "Unknown";
				String position = p.getPosition();
				return new GameResponseDto.PlayerInGameDto(playerName, championName, position);
			})
			.toList();

		return new GameResponseDto.TeamInGameDto(
			first.getTeam().getName(),
			first.getIsWin(),
			first.getSide(),
			playerDtos
		);
	}
}