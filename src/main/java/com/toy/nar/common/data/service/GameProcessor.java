package com.toy.nar.common.data.service;

import com.toy.nar.common.NameNormalizer;
import com.toy.nar.common.dto.GameDataCsvDto;
import com.toy.nar.game.entity.Ban;
import com.toy.nar.game.entity.Game;
import com.toy.nar.game.entity.GameParticipant;
import com.toy.nar.game.entity.League;
import com.toy.nar.participant.entity.Champion;
import com.toy.nar.participant.entity.Player;
import com.toy.nar.participant.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GameProcessor {

	private final EntityResolver entityResolver;
	private static final DateTimeFormatter CSV_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	// 역할: 처리된 Game과 Participant를 함께 전달하기 위한 record
	public record ProcessedData(Game game, GameParticipant participant) {}

	/**
	 * 책임: DTO 한 줄을 Game, GameParticipant 엔티티로 변환합니다.
	 * @param dto CSV 데이터 한 줄
	 * @param gameCacheInChunk 청크 내에서 Game 객체의 재사용을 위한 캐시
	 * @return 변환 성공 시 ProcessedData를 담은 Optional, 실패 시 empty
	 */
	public Optional<ProcessedData> process(GameDataCsvDto dto, Map<String, Game> gameCacheInChunk) {
		// 1. EntityResolver를 통해 연관 엔티티 조회
		Team team = entityResolver.getTeamCache().get(dto.getTeamname());
		Player player = entityResolver.getPlayerCache().get(dto.getPlayername());
		Champion champion = entityResolver.getChampionCache().get(NameNormalizer.normalizeChampionName(dto.getChampion()));
		League league = entityResolver.getLeagueCache().get(DataIngestionFacade.LeagueIdentifier.fromDto(dto));

		// 2. 유효성 검증
		if (team == null || player == null || champion == null || league == null) {
			log.warn("Skipping row due to missing entity. GameID: {}, Team: {}, Player: {}, Champion: {}, League: {}",
				dto.getGameid(), dto.getTeamname(), dto.getPlayername(), dto.getChampion(), dto.getLeague());
			return Optional.empty();
		}

		// 3. Game 엔티티 생성 또는 재사용
		Game game = gameCacheInChunk.computeIfAbsent(dto.getGameid(), gameId -> createGame(dto, league));

		// 4. Ban 정보 추가 (Game 객체에 직접 추가)
		addBansToGame(game, team, dto);

		// 5. GameParticipant 엔티티 생성
		GameParticipant participant = createGameParticipant(dto, game, team, player, champion);

		return Optional.of(new ProcessedData(game, participant));
	}

	private Game createGame(GameDataCsvDto dto, League league) {
		return Game.builder()
			.gameOriginId(dto.getGameid())
			.league(league)
			.gameDate(LocalDate.parse(dto.getDate(), CSV_DATE_FORMATTER))
			.gameNumber(dto.getGame())
			.patch(dto.getPatch())
			.gameLengthSeconds(dto.getGamelength())
			.build();
	}

	private GameParticipant createGameParticipant(GameDataCsvDto dto, Game game, Team team, Player player, Champion champion) {
		GameParticipant participant = GameParticipant.builder()
			.game(game)
			.team(team)
			.player(player)
			.champion(champion)
			.side(dto.getSide())
			.position(dto.getPosition())
			.isWin(dto.getResult() == 1)
			.build();
		// 양방향 연관관계 설정
		return participant;
	}

	private void addBansToGame(Game game, Team team, GameDataCsvDto dto) {
		addBan(game, team, dto.getBan1());
		addBan(game, team, dto.getBan2());
		addBan(game, team, dto.getBan3());
		addBan(game, team, dto.getBan4());
		addBan(game, team, dto.getBan5());
	}

	private void addBan(Game game, Team team, String banName) {
		if (StringUtils.hasText(banName)) {
			Champion bannedChampion = entityResolver.getChampionCache().get(NameNormalizer.normalizeChampionName(banName));
			if (bannedChampion != null) {
				Ban ban = Ban.builder()
					.game(game)
					.team(team)
					.bannedChampion(bannedChampion)
					.build();
				// 중복 방지 로직 (이미 Ban 객체에 EqualsAndHashCode가 잘 정의되어 있다면 Set이 효과적)
				if (!game.getBans().contains(ban)) {
					game.getBans().add(ban);
				}
			}
		}
	}
}