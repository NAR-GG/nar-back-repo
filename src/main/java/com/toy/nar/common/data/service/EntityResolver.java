package com.toy.nar.common.data.service;

import com.toy.nar.common.NameNormalizer;
import com.toy.nar.common.dto.GameDataCsvDto;
import com.toy.nar.game.entity.League;
import com.toy.nar.participant.entity.Champion;
import com.toy.nar.participant.entity.Player;
import com.toy.nar.participant.entity.Team;
import com.toy.nar.game.repository.LeagueRepository;
import com.toy.nar.participant.repository.ChampionRepository;
import com.toy.nar.participant.repository.PlayerRepository;
import com.toy.nar.participant.repository.TeamRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

	/**
	 * 역할: 애플리케이션 시작 시, 자주 변경되지 않는 데이터를 미리 캐시에 적재합니다.
	 * [변경] 리그 정보도 챔피언처럼 시작 시점에 한 번만 캐싱하여 성능을 향상시킵니다.
	 */
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
		if (leagueCache.isEmpty()) {
			leagueCache.putAll(leagueRepository.findAll().stream()
				.collect(Collectors.toMap(
					DataIngestionFacade.LeagueIdentifier::fromEntity,
					Function.identity()
				)));
			log.info("Initialized League cache with {} entries.", leagueCache.size());
		}
	}

	/**
	 * 책임: 청크 데이터를 기반으로 필요한 동적 엔티티(팀, 선수)를 DB에 저장하고 캐시를 최신화합니다.
	 * [변경] 리그 해석 로직은 initializeCaches로 이동하여 더 이상 여기서 호출하지 않습니다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void resolveEntitiesFromChunk(List<GameDataCsvDto> chunk) {
		Set<String> requiredTeamNames = chunk.stream()
			.map(GameDataCsvDto::getTeamname)
			.collect(Collectors.toSet());
		Set<String> requiredPlayerNames = chunk.stream()
			.map(GameDataCsvDto::getPlayername)
			.collect(Collectors.toSet());

		resolveTeams(requiredTeamNames);
		resolvePlayers(requiredPlayerNames);
	}

	// [변경] Team 해석 로직이 제네릭 메서드를 호출하도록 간소화
	private void resolveTeams(Set<String> names) {
		resolveEntitiesByName(
			names,
			teamCache,
			teamRepository::findAllByNameInIgnoreCase, // 메서드 참조
			name -> Team.builder().name(name).build(), // 생성 람다
			teamRepository
		);
	}

	// [변경] Player 해석 로직이 제네릭 메서드를 호출하도록 간소화
	private void resolvePlayers(Set<String> names) {
		resolveEntitiesByName(
			names,
			playerCache,
			playerRepository::findAllByNameInIgnoreCase, // 메서드 참조
			name -> Player.builder().name(name).build(), // 생성 람다
			playerRepository
		);
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
		Set<String> requiredNames,
		Map<String, T> cache,
		Function<Set<String>, List<T>> findInDb,
		Function<String, T> entityCreator,
		JpaRepository<T, ID> repository
	) {
		// 캐시에 없는 새로운 이름만 필터링
		Set<String> newNames = requiredNames.stream()
			.filter(name -> !cache.containsKey(name))
			.collect(Collectors.toSet());

		if (newNames.isEmpty()) return;

		// 1. DB에서 기존 엔티티 조회 후 캐시에 추가
		List<T> existingEntities = findInDb.apply(newNames);
		existingEntities.forEach(entity -> cache.put(getNameFromEntity(entity), entity));

		// 2. 여전히 캐시에 없는 이름(DB에도 없던 이름)들을 찾아 새로 생성
		Set<String> namesToCreate = newNames.stream()
			.filter(name -> !cache.containsKey(name))
			.collect(Collectors.toSet());

		if (!namesToCreate.isEmpty()) {
			List<T> newEntities = namesToCreate.stream()
				.map(entityCreator)
				.toList();

			// 3. 새로 생성한 엔티티들을 DB에 저장
			List<T> savedEntities = repository.saveAll(newEntities);

			// 4. 저장된 엔티티(ID가 부여된)를 캐시에 추가
			savedEntities.forEach(entity -> cache.put(getNameFromEntity(entity), entity));
			log.info("Saved and cached {} new {}(s).", savedEntities.size(), savedEntities.get(0).getClass().getSimpleName());
		}
	}

	// 제네릭 메서드에서 엔티티의 이름을 가져오기 위한 헬퍼 메서드
	private <T> String getNameFromEntity(T entity) {
		if (entity instanceof Team) {
			return ((Team) entity).getName();
		} else if (entity instanceof Player) {
			return ((Player) entity).getName();
		}
		throw new IllegalArgumentException("Unsupported entity type for getName: " + entity.getClass().getName());
	}
}