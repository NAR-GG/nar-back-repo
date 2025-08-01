package com.toy.nar.app.data.ingestion;

import com.toy.nar.app.data.ingestion.dto.LeagueIdentifier;
import com.toy.nar.common.util.NameNormalizer;
import com.toy.nar.app.data.ingestion.dto.GameDataCsvDto;
import com.toy.nar.domain.game.entity.Ban;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.GamePlayerStat;
import com.toy.nar.domain.participant.entity.GameTeamStat;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GameProcessor {

	private final EntityResolver entityResolver;
	public static final DateTimeFormatter CSV_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	// 역할: 처리된 Game과 Participant를 함께 전달하기 위한 record
	public record ProcessedData(Game game, GameParticipant participant) {}

	/**
	 * 책임: DTO 한 줄을 Game, GameParticipant 엔티티로 변환합니다.
	 * @param dto CSV 데이터 한 줄
	 * @param gameCache 청크 내에서 Game 객체의 재사용을 위한 캐시
	 * @return 변환 성공 시 ProcessedData를 담은 Optional, 실패 시 empty
	 */
	public Optional<ProcessedData> process(GameDataCsvDto dto, Map<String, Game> gameCache, LocalDateTime scheduledTime) {
		// 1. EntityResolver를 통해 연관 엔티티 조회 (표준화된 Key 사용)
		Team team = entityResolver.resolveTeam(dto.getTeamname());
		Player player = entityResolver.resolvePlayer(dto.getPlayername());
		Champion champion = entityResolver.resolveChampion(dto.getChampion());
		League league = entityResolver.resolveLeague(dto);

		// 2. 유효성 검증
		if (team == null || player == null || champion == null || league == null) {
			log.warn("Skipping player row due to missing entity for gameId: {}", dto.getGameid());
			return Optional.empty();
		}

		// 3. Game 엔티티 생성 또는 재사용
		Game game = gameCache.computeIfAbsent(dto.getGameid(),
			gameId -> Game.from(dto, league, scheduledTime));

		// 4. Ban 정보 추가 (Game 객체에 직접 추가)
		addBansToGame(game, team, dto);

		// 5. GameParticipant 엔티티 생성
		GameParticipant participant = GameParticipant.from(game, team, player, champion, dto);
		GamePlayerStat playerStat = GamePlayerStat.from(participant, dto);
		participant.setStat(playerStat); // 연관관계 설정

		game.addParticipant(participant);
		return Optional.of(new ProcessedData(game, participant));
	}

	public Optional<GameTeamStat> processTeamStats(GameDataCsvDto dto, Game game) {
		Team team = entityResolver.resolveTeam(dto.getTeamname());
		if (team == null) {
			log.warn("Skipping team stat row due to missing team. GameID: {}, TeamName: {}", dto.getGameid(), dto.getTeamname());
			return Optional.empty();
		}

		return Optional.of(GameTeamStat.from(game, team, dto));
	}

	private Game createGame(GameDataCsvDto dto, League league, LocalDateTime scheduledGameStartTime) {
		return Game.builder()
			.gameOriginId(dto.getGameid())
			.league(league)
			.actualGameStartTime(LocalDateTime.parse(dto.getDate(), CSV_DATE_FORMATTER))
			.scheduledGameStartTime(scheduledGameStartTime)
			.gameNumber(dto.getGame())
			.patch(dto.getPatch())
			.gameLengthSeconds(dto.getGamelength())
			.build();
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
				game.getBans().add(ban);

			}
		}
	}
}