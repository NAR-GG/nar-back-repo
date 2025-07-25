package com.toy.nar.app.data.ingestion;

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

	@Getter private final Map<DataIngestionFacade.LeagueIdentifier, League> leagueCache = new ConcurrentHashMap<>();
	@Getter private final Map<String, Team> teamCache = new ConcurrentHashMap<>();
	@Getter private final Map<String, Player> playerCache = new ConcurrentHashMap<>();
	@Getter private final Map<String, Champion> championCache = new ConcurrentHashMap<>();

	@Transactional(readOnly = true)
	public void initializeCaches() {
		if (championCache.isEmpty()) {
			championCache.putAll(championRepository.findAll().stream()
				.collect(Collectors.toMap(
					champion -> NameNormalizer.normalizeChampionName(champion.getChampionNameEn()),
					Function.identity()
				)));
			log.info("Initialized Champion cache with {} entries.", championCache.size());
		}
	}

	/**
	 * 책임: 청크 데이터를 기반으로 필요한 동적 엔티티(팀, 선수)를 DB에 저장하고 캐시를 최신화합니다.
	 */
	@Transactional
	public void resolveEntitiesFromChunk(List<GameDataCsvDto> chunk) {
		Set<String> requiredTeamNames = chunk.stream()
			.map(GameDataCsvDto::getTeamname)
			.collect(Collectors.toSet());
		Set<String> requiredPlayerNames = chunk.stream()
			.map(GameDataCsvDto::getPlayername)
			.collect(Collectors.toSet());
		Set<DataIngestionFacade.LeagueIdentifier> requiredLeagueIds = chunk.stream()
			.map(DataIngestionFacade.LeagueIdentifier::fromDto).collect(Collectors.toSet());


		resolveTeams(requiredTeamNames);
		resolvePlayers(requiredPlayerNames);
		log.debug("Resolving {} leagues from chunk.", requiredLeagueIds.size());
		resolveLeagues(requiredLeagueIds);
		log.debug("After resolve: League cache size: {}, Champion cache size: {}", leagueCache.size(), championCache.size());
	}

	private void resolveTeams(Set<String> originalNames) {
		resolveEntitiesByName(
			originalNames,
			NameNormalizer::normalizeTeamName,
			teamCache,
			teamRepository::findAllByNameInIgnoreCase,
			// [수정] .build()를 포함한 람다식으로 변경
			name -> Team.builder().name(name).build(),
			teamRepository
		);
	}

	private void resolvePlayers(Set<String> originalNames) {
		resolveEntitiesByName(
			originalNames,
			NameNormalizer::normalizePlayerName,
			playerCache,
			playerRepository::findAllByNameInIgnoreCase,
			// [수정] .build()를 포함한 람다식으로 변경
			name -> Player.builder().name(name).build(),
			playerRepository
		);
	}

	private void resolveLeagues(Set<DataIngestionFacade.LeagueIdentifier> requiredIds) {
		log.debug("Required league IDs: {}", requiredIds);
		requiredIds.forEach(id ->
			leagueCache.computeIfAbsent(id, this::findOrCreateLeague)
		);
		log.debug("League cache after resolve: {}", leagueCache.keySet());
	}

	private League findOrCreateLeague(DataIngestionFacade.LeagueIdentifier id) {
		// 1. 먼저 DB에서 찾아본다.
		return leagueRepository.findByLeagueNameAndSeasonYearAndSeasonSplitAndIsPlayoffs(
			id.name(), id.year(), id.split(), id.isPlayoffs()
		).orElseGet(() -> {
			// 2. DB에 없으면 새로 생성한다.
			log.info("Creating new league: {} (name: {}, year: {}, split: {}, playoffs: {})",
				id, id.name(), id.year(), id.split(), id.isPlayoffs());  // 상세 로그 추가
			League newLeague = League.builder()
				.leagueName(id.name())
				.seasonYear(id.year())
				.seasonSplit(id.split())
				.isPlayoffs(id.isPlayoffs())
				.build();

			League saved = leagueRepository.save(newLeague);
			log.info("Saved new league with ID: {}", saved.getId());  // 저장 확인 로그
			return saved;
		});
	}

	/**
	 * [신규] 이름 기반 엔티티(Team, Player)의 중복된 조회/생성 로직을 통합한 제네릭 메서드입니다.
	 * 역할: 이름으로 엔티티를 찾거나, 없으면 생성하여 캐시에 저장합니다.
	 *
	 * @param requiredNames      필요한 엔티티 이름 목록
	 * @param cache              해당 엔티티의 캐시
	 * @param findInDb           DB에서 이름으로 찾는 기능을 하는 메서드
	 * @param entityCreator      이름으로 새 엔티티를 생성하는 함수
	 * @param repository         저장을 위한 JpaRepository
	 * @param <T>                엔티티 타입 (Team 또는 Player)
	 * @param <ID>               엔티티의 ID 타입
	 */
	private <T, ID> void resolveEntitiesByName(
		Set<String> originalNames,
		Function<String, String> storageNormalizer, // 이름 표준화 함수(Title Case 변환용)
		Map<String, T> cache,
		Function<Set<String>, List<T>> findInDb,
		Function<String, T> entityCreator,
		JpaRepository<T, ID> repository
	) {
		// 조회용 Key(lowercase)를 기준으로 캐시에 없는 원본 이름만 필터링
		Set<String> newOriginalNames = originalNames.stream()
			.filter(name -> StringUtils.hasText(name) && !cache.containsKey(storageNormalizer.apply(name.trim())))
			.collect(Collectors.toSet());

		if (newOriginalNames.isEmpty()) return;

		Set<String> lookupKeysToFind = newOriginalNames.stream()
			.map(name -> storageNormalizer.apply(name.trim()))
			.collect(Collectors.toSet());

		log.debug("Querying DB for {} with keys: {}", repository.getClass().getSimpleName(), lookupKeysToFind);

		// 1. DB에서 조회 후 캐시에 추가
		List<T> existingEntities = findInDb.apply(lookupKeysToFind);
		log.debug("Found {} existing entities from DB.", existingEntities.size());

		existingEntities.forEach(entity -> {
			String normalizedName = getNameFromEntity(entity); // 이미 normalized 되어 있어야 함
			cache.put(normalizedName, entity);
		});

		// 2. DB에도 없어 최종적으로 새로 생성해야 할 이름 필터링
		Set<String> namesToCreate = lookupKeysToFind.stream() // 이미 normalized Set 사용
			.filter(normalizedName -> !cache.containsKey(normalizedName))
			.collect(Collectors.toSet());

		log.debug("Names to create (after DB check): {}", namesToCreate);

		if (!namesToCreate.isEmpty()) {
			Map<String, T> uniqueEntitiesToCreate = new HashMap<>();
			namesToCreate.forEach(normalizedName -> {
				// 이미 normalizedName이 키이므로 putIfAbsent
				uniqueEntitiesToCreate.putIfAbsent(normalizedName, entityCreator.apply(normalizedName));
			});
			List<T> newEntities = new ArrayList<>(uniqueEntitiesToCreate.values());

			if (!newEntities.isEmpty()) {
				log.info("Attempting to save {} new entities.", newEntities.size());
				List<T> savedEntities = repository.saveAll(newEntities);

				savedEntities.forEach(entity -> {
					String normalizedName = getNameFromEntity(entity);
					cache.put(normalizedName, entity);
				});
				log.info("Saved and cached {} new {}(s).", savedEntities.size(), savedEntities.get(0).getClass().getSimpleName());
			}
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
}