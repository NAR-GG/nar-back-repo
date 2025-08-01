package com.toy.nar.app.record;

import com.toy.nar.app.record.dto.BansDto;
import com.toy.nar.app.record.dto.GameRecordDto;
import com.toy.nar.app.record.dto.PlayerRecordDto;
import com.toy.nar.domain.game.entity.*;
import com.toy.nar.domain.game.repository.GameRepository;
import com.toy.nar.domain.participant.entity.GamePlayerStat;
import com.toy.nar.domain.participant.entity.GameTeamStat;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameRecordService {

	private final GameRepository gameRepository;
	private final GameTeamStatRepository gameTeamStatRepository;

	public GameRecordDto getGameRecord(Long gameId) {
		// 1. Repository에서 한 번의 쿼리로 모든 데이터를 가져옴
		Game game = gameRepository.findGameDetailsById(gameId)
			.orElseThrow(() -> new IllegalArgumentException("Game not found with id: " + gameId));

		// 2. 해당 게임의 TeamStat을 조회 (Blue/Red 팀별로)
		Map<String, GameTeamStat> teamStats = gameTeamStatRepository.findByGameId(game.getId())
			.stream()
			.collect(Collectors.toMap(stat -> stat.getTeam().getName(), stat -> stat));

		// 3. 데이터를 DTO로 변환
		// 3-1. Ban 정보 변환
		List<String> blueBans = game.getBans().stream()
			.filter(b -> b.getTeam().getName().equals(game.getParticipants().stream().filter(p -> p.getSide().equalsIgnoreCase("blue")).findFirst().get().getTeam().getName()))
			.map(b -> b.getBannedChampion().getChampionNameEn())
			.toList();

		List<String> redBans = game.getBans().stream()
			.filter(b -> b.getTeam().getName().equals(game.getParticipants().stream().filter(p -> p.getSide().equalsIgnoreCase("red")).findFirst().get().getTeam().getName()))
			.map(b -> b.getBannedChampion().getChampionNameEn())
			.toList();

		BansDto bansDto = new BansDto(blueBans, redBans);

		// 3-2. Player 정보 변환 (핵심 로직)
		List<PlayerRecordDto> playerRecordDtos = game.getParticipants().stream()
			.distinct()
			.map(p -> {
				GameTeamStat teamStat = teamStats.get(p.getTeam().getName());
				return PlayerRecordDto.from(p, teamStat);
			}).toList();

		// 3-3. 최종 GameRecordDto 조립
		return new GameRecordDto(
			game.getGameOriginId(), "complete", game.getLeague().getLeagueName(),
			game.getLeague().getSeasonYear(), game.getLeague().getSeasonSplit(), game.getLeague().getIsPlayoffs() ? 1 : 0,
			game.getActualGameStartTime().toLocalDate().toString(), game.getGameNumber(), game.getPatch(),
			game.getGameLengthSeconds(), bansDto, playerRecordDtos
		);
	}
}