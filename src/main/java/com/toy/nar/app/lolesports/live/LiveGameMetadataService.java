package com.toy.nar.app.lolesports.live;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.toy.nar.app.lolesports.LeagueConfigService;
import com.toy.nar.app.lolesports.MatchResponseWrapper;
import com.toy.nar.app.lolesports.MatchResultDto;
import com.toy.nar.app.lolesports.WorldsService;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchGame;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveGameMetadataService {

	private final WorldsService worldsService;
	private final LeagueMatchGameRepository leagueMatchGameRepository;
	private final LeagueConfigService leagueConfigService;
	private final Cache<String, Optional<ActiveLiveGame>> metadataByGameId = Caffeine.newBuilder()
			.expireAfterWrite(60, TimeUnit.SECONDS)
			.maximumSize(1_000)
			.build();

	public ActiveLiveGame enrich(ActiveLiveGame activeGame) {
		if (activeGame == null || activeGame.gameId() == null || activeGame.gameId().isBlank()) {
			return activeGame;
		}
		return activeGame.mergeMissingMetadata(resolve(activeGame.gameId()).orElse(null));
	}

	public Optional<ActiveLiveGame> resolve(String gameId) {
		if (gameId == null || gameId.isBlank()) {
			return Optional.empty();
		}
		return metadataByGameId.get(gameId, this::loadMetadata);
	}

	public void remember(ActiveLiveGame activeGame) {
		if (activeGame == null || activeGame.gameId() == null || activeGame.gameId().isBlank()) {
			return;
		}
		metadataByGameId.put(activeGame.gameId(), Optional.of(activeGame));
	}

	private Optional<ActiveLiveGame> loadMetadata(String gameId) {
		Optional<ActiveLiveGame> fromDb = loadFromLocalMatchGame(gameId);
		if (fromDb.isPresent()) {
			return fromDb;
		}
		return loadFromSchedule(gameId);
	}

	private Optional<ActiveLiveGame> loadFromLocalMatchGame(String gameId) {
		return leagueMatchGameRepository.findWithMatchByGameId(gameId)
				.map(LeagueMatchGame::getLeagueMatch)
				.map(match -> new ActiveLiveGame(
						gameId,
						match.getId(),
						match.getLeagueName(),
						match.getBlueTeamName(),
						match.getRedTeamName(),
						LocalDateTime.now(ZoneOffset.UTC),
						0));
	}

	private Optional<ActiveLiveGame> loadFromSchedule(String gameId) {
		for (String league : leagueConfigService.liveLeagues()) {
			try {
				MatchResponseWrapper response = worldsService.getWorldsMatches(null, league);
				Optional<ActiveLiveGame> match = response.getMatches().stream()
						.filter(matchDto -> containsGameId(matchDto.getGameIds(), gameId)
								|| containsGameId(matchDto.getLiveGameIds(), gameId))
						.findFirst()
						.map(matchDto -> toActiveLiveGame(gameId, league, matchDto));
				if (match.isPresent()) {
					return match;
				}
			} catch (Exception e) {
				log.warn("Live metadata lookup failed for gameId={} league={}: {}", gameId, league, e.getMessage());
			}
		}
		return Optional.empty();
	}

	private ActiveLiveGame toActiveLiveGame(String gameId, String fallbackLeague, MatchResultDto match) {
		String leagueName = isBlank(match.getLeagueName()) ? fallbackLeague : match.getLeagueName();
		return new ActiveLiveGame(
				gameId,
				match.getMatchId(),
				leagueName,
				match.getBlueTeam() == null ? null : firstNonBlank(match.getBlueTeam().getName(), match.getBlueTeam().getCode()),
				match.getRedTeam() == null ? null : firstNonBlank(match.getRedTeam().getName(), match.getRedTeam().getCode()),
				LocalDateTime.now(ZoneOffset.UTC),
				0);
	}

	private boolean containsGameId(List<String> gameIds, String gameId) {
		return gameIds != null && gameIds.contains(gameId);
	}

	private String firstNonBlank(String first, String second) {
		return isBlank(first) ? second : first;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
