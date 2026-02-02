package com.toy.nar.app.record;

import com.toy.nar.app.record.dto.BansDto;
import com.toy.nar.app.record.dto.GameRecordDto;
import com.toy.nar.app.record.dto.PlayerRecordDto;
import com.toy.nar.app.record.dto.SetNavigationDto;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.game.entity.*;
import com.toy.nar.domain.game.repository.GameRepository;
import com.toy.nar.domain.participant.entity.GameTeamStat;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameRecordService {

	private final GameRepository gameRepository;
	private final GameTeamStatRepository gameTeamStatRepository;

	@Cacheable(value = "gameRecords", key = "#gameId.toString()")
	public GameRecordDto getGameRecord(Long gameId) {
		// 1. Repository에서 한 번의 쿼리로 모든 데이터를 가져옴
		Game game = gameRepository.findGameDetailsById(gameId)
				.orElseThrow(() -> new CustomException(ErrorCode.MATCH_NOT_FOUND));

		// 2. 해당 게임의 TeamStat을 조회 (Blue/Red 팀별로)
		List<GameTeamStat> statList = gameTeamStatRepository.findByGameId(game.getId());

		// 데이터 무결성 체크: 게임은 있는데 팀 스탯이 하나도 없다면 데이터 오류 (500)
		if (statList.isEmpty()) {
			throw new CustomException(ErrorCode.DATA_INTEGRITY_ERROR);
		}

		Map<String, GameTeamStat> teamStats = statList.stream()
				.collect(Collectors.toMap(stat -> stat.getTeam().getName(), stat -> stat));

		// 3. 블루/레드 팀 정보 식별
		Team blueTeam = findTeamBySide(game, "Blue");
		Team redTeam = findTeamBySide(game, "Red");

		// 4. Bans 변환
		List<String> blueBans = extractBansByTeam(game, blueTeam.getName());
		List<String> redBans = extractBansByTeam(game, redTeam.getName());

		BansDto bansDto = new BansDto(blueBans, redBans);

		// 5. Player 정보 변환
		List<PlayerRecordDto> playerRecordDtos = game.getParticipants().stream()
				.distinct()
				.map(p -> {
					String teamName = p.getTeam().getName();
					GameTeamStat teamStat = teamStats.get(teamName);

					if (teamStat == null) {
						log.error("Team stat missing for team: {} in game: {}", teamName, gameId);
						throw new CustomException(ErrorCode.DATA_INTEGRITY_ERROR);
					}
					return PlayerRecordDto.from(p, teamStat);
				}).toList();

		// 6. 세트 네비게이션 정보 조회
		SetNavigationDto setNav = buildSetNavigation(game, blueTeam, redTeam);

		// 7. 최종 GameRecordDto 조립
		return new GameRecordDto(
				game.getGameOriginId(), "complete", game.getLeague().getLeagueName(),
				game.getLeague().getSeasonYear(), game.getLeague().getSeasonSplit(),
				game.getLeague().getIsPlayoffs() ? 1 : 0,
				game.getActualGameStartTime().toLocalDate().toString(), game.getGameNumber(), game.getPatch(),
				game.getGameLengthSeconds(), bansDto, playerRecordDtos, setNav);
	}

	/**
	 * 세트 네비게이션 정보 생성
	 */
	private SetNavigationDto buildSetNavigation(Game currentGame, Team blueTeam, Team redTeam) {
		// 같은 매치의 게임들 조회 (같은 날짜 + 같은 팀 조합)
		List<Game> relatedGames = findRelatedGames(currentGame, blueTeam.getName(), redTeam.getName());

		// 세트 정보 목록
		List<SetNavigationDto.SetInfo> sets = relatedGames.stream()
				.map(g -> new SetNavigationDto.SetInfo(g.getGameNumber(), g.getId()))
				.sorted(Comparator.comparingInt(SetNavigationDto.SetInfo::setNumber))
				.toList();

		// 각 팀의 승점 계산 (세트 승리 수)
		int blueScore = 0;
		int redScore = 0;

		for (Game g : relatedGames) {
			String winner = findWinnerTeamName(g);
			if (blueTeam.getName().equals(winner)) {
				blueScore++;
			} else if (redTeam.getName().equals(winner)) {
				redScore++;
			}
		}

		// 팀 요약 정보
		SetNavigationDto.TeamSummary blueTeamSummary = new SetNavigationDto.TeamSummary(
				blueTeam.getCode(), blueTeam.getName(), blueTeam.getImageUrl(), blueScore);
		SetNavigationDto.TeamSummary redTeamSummary = new SetNavigationDto.TeamSummary(
				redTeam.getCode(), redTeam.getName(), redTeam.getImageUrl(), redScore);

		return new SetNavigationDto(
				currentGame.getGameNumber(),
				relatedGames.size(),
				sets,
				blueTeamSummary,
				redTeamSummary);
	}

	/**
	 * 같은 매치의 관련 게임들 조회 (같은 날짜 + 같은 팀 조합)
	 */
	private List<Game> findRelatedGames(Game currentGame, String teamName1, String teamName2) {
		LocalDateTime gameDate = currentGame.getActualGameStartTime();
		LocalDateTime dayStart = gameDate.toLocalDate().atStartOfDay();
		LocalDateTime dayEnd = dayStart.plusDays(1);

		// 같은 날짜의 모든 게임 조회
		List<Game> gamesOnSameDay = gameRepository.findAllWithParticipantsByActualGameStartTimeBetween(dayStart,
				dayEnd);

		// 같은 팀 조합의 게임만 필터링
		return gamesOnSameDay.stream()
				.filter(g -> {
					String blueTeamInGame = findTeamNameBySide(g, "Blue");
					String redTeamInGame = findTeamNameBySide(g, "Red");

					// 팀 조합 일치 확인 (순서 무관)
					return (blueTeamInGame.equals(teamName1) && redTeamInGame.equals(teamName2))
							|| (blueTeamInGame.equals(teamName2) && redTeamInGame.equals(teamName1));
				})
				.sorted(Comparator.comparingInt(Game::getGameNumber))
				.toList();
	}

	/**
	 * 게임의 승리 팀 이름 반환
	 */
	private String findWinnerTeamName(Game game) {
		return game.getParticipants().stream()
				.filter(GameParticipant::getIsWin)
				.findFirst()
				.map(p -> p.getTeam().getName())
				.orElse("");
	}

	private Team findTeamBySide(Game game, String side) {
		return game.getParticipants().stream()
				.filter(p -> p.getSide().equalsIgnoreCase(side))
				.findFirst()
				.map(GameParticipant::getTeam)
				.orElseThrow(() -> {
					log.error("No participant found for side {} in game {}", side, game.getId());
					return new CustomException(ErrorCode.DATA_INTEGRITY_ERROR);
				});
	}

	private String findTeamNameBySide(Game game, String side) {
		return findTeamBySide(game, side).getName();
	}

	// Helper Method: 팀 이름별 밴 리스트 추출
	private List<String> extractBansByTeam(Game game, String teamName) {
		return game.getBans().stream()
				.filter(b -> b.getTeam().getName().equals(teamName))
				.map(b -> b.getBannedChampion().getChampionNameEn())
				.toList();
	}
}