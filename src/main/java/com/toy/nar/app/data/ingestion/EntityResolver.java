package com.toy.nar.app.data.ingestion;

import com.toy.nar.app.data.ingestion.dto.LeagueIdentifier;
import com.toy.nar.common.util.NameNormalizer;
import com.toy.nar.app.data.ingestion.dto.GameDataCsvDto;
import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.game.repository.LeagueRepository;
import com.toy.nar.domain.participant.repository.ChampionRepository;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntityResolver {

	private final LeagueRepository leagueRepository;
	private final TeamRepository teamRepository;
	private final PlayerRepository playerRepository;
	private final ChampionRepository championRepository;

	@Getter
	private final Map<LeagueIdentifier, League> leagueCache = new ConcurrentHashMap<>();
	@Getter
	private final Map<String, Team> teamCache = new ConcurrentHashMap<>();
	@Getter
	private final Map<String, Player> playerCache = new ConcurrentHashMap<>();
	@Getter
	private final Map<String, Champion> championCache = new ConcurrentHashMap<>();

	@Transactional(readOnly = true)
	public void initializeCaches() {
		if (championCache.isEmpty()) {
			championCache.putAll(championRepository.findAll().stream()
					.collect(Collectors.toMap(
							champion -> NameNormalizer.normalizeChampionName(champion.getChampionNameEn()),
							Function.identity())));
			log.info("Initialized Champion cache with {} entries.", championCache.size());
		}
	}

	@Transactional
	public void resolveEntitiesFromChunk(List<GameDataCsvDto> chunk) {
		Set<String> requiredTeamNames = chunk.stream()
				.map(GameDataCsvDto::getTeamname)
				.collect(Collectors.toSet());
		Set<String> requiredPlayerNames = chunk.stream()
				.map(GameDataCsvDto::getPlayername)
				.collect(Collectors.toSet());
		Set<LeagueIdentifier> requiredLeagueIds = chunk.stream()
				.map(LeagueIdentifier::fromDto).collect(Collectors.toSet());

		resolveTeams(requiredTeamNames);
		resolvePlayers(requiredPlayerNames);
		log.debug("Resolving {} leagues from chunk.", requiredLeagueIds.size());
		resolveLeagues(requiredLeagueIds, requiredTeamNames);
		log.debug("After resolve: League cache size: {}, Champion cache size: {}", leagueCache.size(),
				championCache.size());
	}

	private void resolveTeams(Set<String> originalNames) {
		resolveEntitiesByName(
				originalNames,
				NameNormalizer::normalizeTeamName,
				teamCache,
				teamRepository::findAllByNameInWithLeagueTeams,
				name -> Team.builder().name(name).build(),
				teamRepository);
	}

	private void resolvePlayers(Set<String> originalNames) {
		resolveEntitiesByName(
				originalNames,
				NameNormalizer::normalizePlayerName,
				playerCache,
				playerRepository::findAllByNameInIgnoreCase,
				name -> Player.builder().name(name).build(),
				playerRepository);
	}

	public Team resolveTeam(String teamName) {
		if (!StringUtils.hasText(teamName))
			return null;
		return teamCache.get(NameNormalizer.normalizeTeamName(teamName.trim()));
	}

	public Player resolvePlayer(String playerName) {
		if (!StringUtils.hasText(playerName))
			return null;
		return playerCache.get(NameNormalizer.normalizePlayerName(playerName.trim()));
	}

	public Champion resolveChampion(String championName) {
		if (!StringUtils.hasText(championName))
			return null;
		return championCache.get(NameNormalizer.normalizeChampionName(championName.trim()));
	}

	public League resolveLeague(GameDataCsvDto dto) {
		return leagueCache.get(LeagueIdentifier.fromDto(dto));
	}

	@Transactional
	public void resolveLeagues(Set<LeagueIdentifier> requiredIds, Set<String> requiredTeamNames) {
		Set<LeagueIdentifier> missingInCache = requiredIds.stream()
				.filter(id -> !leagueCache.containsKey(id))
				.collect(Collectors.toSet());

		if (missingInCache.isEmpty()) {
			return;
		}

		Set<String> leagueNamesToFind = missingInCache.stream()
				.map(LeagueIdentifier::name)
				.collect(Collectors.toSet());
		Set<Integer> yearsToFind = missingInCache.stream()
				.map(LeagueIdentifier::year)
				.collect(Collectors.toSet());
		List<League> foundLeagues = leagueRepository.findLeaguesWithTeamsByIdentifiers(leagueNamesToFind, yearsToFind);

		Map<LeagueIdentifier, League> foundLeaguesMap = foundLeagues.stream()
				.collect(Collectors.toMap(LeagueIdentifier::fromEntity, Function.identity()));
		leagueCache.putAll(foundLeaguesMap);

		List<League> leaguesToCreate = new ArrayList<>();
		missingInCache.stream()
				.filter(id -> !foundLeaguesMap.containsKey(id))
				.forEach(id -> {
					League newLeague = League.builder()
							.leagueName(id.name())
							.seasonYear(id.year())
							.seasonSplit(id.split())
							.isPlayoffs(id.isPlayoffs())
							.build();

					requiredTeamNames.forEach(teamName -> {
						String teamLookupKey = NameNormalizer.normalizeTeamName(teamName);
						Team team = teamCache.get(teamLookupKey);
						if (team != null) {
							newLeague.addLeagueTeam(team);
						}
					});
					leaguesToCreate.add(newLeague);
				});

		if (!leaguesToCreate.isEmpty()) {
			log.info("Creating {} new leagues.", leaguesToCreate.size());
			List<League> savedLeagues = leagueRepository.saveAll(leaguesToCreate);

			savedLeagues.forEach(league -> leagueCache.put(LeagueIdentifier.fromEntity(league), league));
			log.info("Successfully created and cached {} new leagues.", savedLeagues.size());
		}
	}

	private <T, ID> void resolveEntitiesByName(
			Set<String> originalNames,
			Function<String, String> storageNormalizer,
			Map<String, T> cache,
			Function<Set<String>, List<T>> findInDb,
			Function<String, T> entityCreator,
			JpaRepository<T, ID> repository) {
		Set<String> newNormalizedNames = originalNames.stream()
				.filter(StringUtils::hasText)
				.map(name -> storageNormalizer.apply(name.trim()))
				.filter(normalizedName -> !cache.containsKey(normalizedName))
				.collect(Collectors.toSet());

		if (newNormalizedNames.isEmpty())
			return;

		log.debug("Querying DB for {} with keys: {}", repository.getClass().getSimpleName(), newNormalizedNames);
		List<T> existingEntities = findInDb.apply(newNormalizedNames);
		log.debug("Found {} existing entities from DB.", existingEntities.size());

		existingEntities.forEach(entity -> {
			String normalizedName = storageNormalizer.apply(getNameFromEntity(entity));
			cache.put(normalizedName, entity);
		});

		Set<String> namesOfExistingEntities = existingEntities.stream()
				.map(entity -> storageNormalizer.apply(getNameFromEntity(entity)))
				.collect(Collectors.toSet());

		List<T> entitiesToCreate = newNormalizedNames.stream()
				.filter(normalizedName -> !namesOfExistingEntities.contains(normalizedName))
				.map(entityCreator)
				.collect(Collectors.toList());

		if (!entitiesToCreate.isEmpty()) {
			log.info("Creating {} new entities for {}.", entitiesToCreate.size(),
					repository.getClass().getSimpleName());
			List<T> savedEntities = repository.saveAll(entitiesToCreate);
			log.info("Successfully created {} new entities.", savedEntities.size());

			savedEntities.forEach(entity -> {
				String normalizedName = getNameFromEntity(entity);
				cache.put(normalizedName, entity);
			});
		}
	}

	private <T> String getNameFromEntity(T entity) {
		if (entity instanceof Team) {
			return ((Team) entity).getName();
		} else if (entity instanceof Player) {
			return ((Player) entity).getName();
		}
		throw new IllegalArgumentException("Unsupported entity type for getName: " + entity.getClass().getName());
	}

	public void clearCaches() {
		leagueCache.clear();
		teamCache.clear();
		playerCache.clear();
		championCache.clear();
		log.info("EntityResolver caches cleared.");
	}
}
