package com.toy.nar.app.mobile.match;

import com.toy.nar.app.lolesports.live.ItemMetadataResolver;
import com.toy.nar.app.lolesports.live.LiveStateQueryService;
import com.toy.nar.app.lolesports.live.RuneMetadataResolver;
import com.toy.nar.app.lolesports.live.dto.LiveObjectEventResponse;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveParticipantState;
import com.toy.nar.app.lolesports.live.entity.LiveGameMapping;
import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteSnapshot;
import com.toy.nar.app.lolesports.live.entity.LiveGameObjectEvent;
import com.toy.nar.app.lolesports.live.repository.LiveGameMappingRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameObjectEventRepository;
import com.toy.nar.app.mobile.match.dto.LiveGameChampionsResponse;
import com.toy.nar.app.mobile.match.dto.LiveGameEventsResponse;
import com.toy.nar.common.util.NameNormalizer;
import com.toy.nar.domain.game.repository.BanRepository;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.ChampionRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileLiveGameService {

	private static final String BLUE = "Blue";
	private static final String RED = "Red";
	// game_participants.side 를 대문자화한 진영 키 (밴 그룹핑용).
	private static final String BLUE_SIDE_KEY = "BLUE";
	private static final String RED_SIDE_KEY = "RED";
	private static final String EVENT_KILL = "KILL";
	private static final String EVENT_DRAGON = "DRAGON";
	private static final String EVENT_BARON = "BARON";
	private static final String EVENT_TOWER = "TOWER";
	private static final String EVENT_INHIBITOR = "INHIBITOR";
	private static final String ELDER = "elder";

	// 픽 정렬 기준: top → jungle → mid → bottom → support. 알 수 없는 역할은 맨 뒤.
	private static final Map<String, Integer> ROLE_ORDER = Map.of(
			"top", 0,
			"jungle", 1,
			"mid", 2,
			"bottom", 3,
			"support", 4);

	private final LiveStateQueryService liveStateQueryService;
	private final LiveGameObjectEventRepository objectEventRepository;
	private final LiveGameMinuteSnapshotRepository minuteSnapshotRepository;
	private final ChampionRepository championRepository;
	private final TeamRepository teamRepository;
	private final LiveGameMappingRepository liveGameMappingRepository;
	private final BanRepository banRepository;
	private final RuneMetadataResolver runeMetadataResolver;
	private final ItemMetadataResolver itemMetadataResolver;

	public LiveGameChampionsResponse getChampions(String gameId) {
		LiveGameState state = requireState(gameId);

		List<LiveParticipantState> blue = state.participants().stream()
				.filter(p -> BLUE.equalsIgnoreCase(p.teamSide()))
				.sorted(pickComparator())
				.toList();
		List<LiveParticipantState> red = state.participants().stream()
				.filter(p -> RED.equalsIgnoreCase(p.teamSide()))
				.sorted(pickComparator())
				.toList();

		Map<String, List<LiveGameChampionsResponse.Ban>> bansBySide = resolveBans(gameId);

		// 오브젝트는 getLatestState 가 이미 실어 온 타임라인에서 센다(추가 조회 없음).
		return new LiveGameChampionsResponse(
				gameId,
				state.frameTimestampUtc(),
				toTeamChampions(state.blueTeamName(), blue,
						bansBySide.getOrDefault(BLUE_SIDE_KEY, List.of())),
				toTeamChampions(state.redTeamName(), red,
						bansBySide.getOrDefault(RED_SIDE_KEY, List.of())),
				new LiveGameChampionsResponse.Objectives(
						countObjectives(state.objectTimeline(), BLUE),
						countObjectives(state.objectTimeline(), RED)));
	}

	/**
	 * 한 진영의 오브젝트 획득 수를 센다.
	 *
	 * <p>이벤트는 (game, side, type, order) 유니크라 중복이 없어 단순 카운트로 충분하다.
	 * {@code valueAfter} 최댓값을 쓰지 않는 이유는 그 값이 장로용을 드래곤 카운터에 포함하기
	 * 때문이다(피드 window 의 {@code dragons[]} 인덱스를 그대로 쓴다).
	 */
	private LiveGameChampionsResponse.TeamObjectives countObjectives(
			List<LiveObjectEventResponse> timeline, String teamSide) {
		List<String> dragonTypes = new ArrayList<>();
		int elders = 0;
		int barons = 0;
		int towers = 0;
		int inhibitors = 0;

		for (LiveObjectEventResponse event : timeline) {
			if (!teamSide.equalsIgnoreCase(event.teamSide())) {
				continue;
			}
			switch (event.eventType() == null ? "" : event.eventType()) {
				case EVENT_DRAGON -> {
					if (ELDER.equalsIgnoreCase(event.eventSubType())) {
						elders++;
					} else if (event.eventSubType() != null) {
						dragonTypes.add(event.eventSubType());
					}
				}
				case EVENT_BARON -> barons++;
				case EVENT_TOWER -> towers++;
				case EVENT_INHIBITOR -> inhibitors++;
				default -> {
					// KILL 등 오브젝트가 아닌 이벤트는 센지 않는다.
				}
			}
		}

		return new LiveGameChampionsResponse.TeamObjectives(
				dragonTypes.size(), dragonTypes, elders, barons, towers, inhibitors);
	}

	/**
	 * 라이브 gameId 를 reconcile 된 배치 game 으로 매핑해 진영(BLUE/RED)별 밴 목록을 만든다.
	 *
	 * <p>라이브 피드에는 밴이 없어 배치 {@code bans} 테이블에서 가져온다. 아직 reconcile 되지
	 * 않았거나(매핑의 internalGameId 가 null) 배치 적재 전이면 빈 맵을 반환한다. 배치는 6시간
	 * 주기 적재라, 갓 끝난 경기의 밴은 다음 적재 + reconcile 이후에 노출된다(그 전엔 빈 목록).
	 */
	private Map<String, List<LiveGameChampionsResponse.Ban>> resolveBans(String liveGameId) {
		Long internalGameId = liveGameMappingRepository.findByLiveGameId(liveGameId)
				.map(LiveGameMapping::getInternalGameId)
				.orElse(null);
		if (internalGameId == null) {
			return Map.of();
		}
		return banRepository.findLiveBanRowsByGameId(internalGameId).stream()
				.filter(row -> row.getSide() != null)
				.collect(Collectors.groupingBy(
						row -> row.getSide().toUpperCase(Locale.ROOT),
						Collectors.mapping(
								row -> new LiveGameChampionsResponse.Ban(
										row.getChampionName(), row.getImageUrl()),
								Collectors.toList())));
	}

	public LiveGameEventsResponse getEvents(String gameId) {
		LiveGameState state = liveStateQueryService.getLatestState(gameId).orElse(null);
		String blueTeamName = state == null ? null : state.blueTeamName();
		String redTeamName = state == null ? null : state.redTeamName();
		// 팀 로고는 요청당 한 번씩만 조회한다(이벤트마다 조회하지 않는다).
		String blueTeamImageUrl = resolveTeamImageUrl(blueTeamName);
		String redTeamImageUrl = resolveTeamImageUrl(redTeamName);

		List<LiveGameObjectEvent> events = objectEventRepository
				.findByGameIdOrderBySourceFrameTimestampUtcAscIdAsc(gameId);

		// gameTime 기준점(t0)은 "이 경기에서 처음 관측된 분 스냅샷 프레임" = 게임 시작 무렵.
		// (첫 킬/이벤트가 아니라 폴링 시작 시점이므로 인게임 시계와 거의 맞는다. 스냅샷은 재기동에도 DB에 보존됨)
		// 분 스냅샷이 전혀 없으면 최초 이벤트로 폴백(근사).
		LocalDateTime t0 = minuteSnapshotRepository.findTopByGameIdOrderByMinuteBucketUtcAsc(gameId)
				.map(LiveGameMinuteSnapshot::getFrameTimestampUtc)
				.orElseGet(() -> events.stream()
						.map(LiveGameObjectEvent::getSourceFrameTimestampUtc)
						.min(Comparator.naturalOrder())
						.orElse(null));

		List<LiveGameEventsResponse.Event> mapped = new ArrayList<>();
		for (LiveGameObjectEvent event : events) {
			mapped.add(toEvent(event, t0, blueTeamName, redTeamName));
		}
		// 최신순(newest first)으로 뒤집는다.
		java.util.Collections.reverse(mapped);

		return new LiveGameEventsResponse(
				gameId,
				blueTeamName, blueTeamImageUrl,
				redTeamName, redTeamImageUrl,
				mapped);
	}

	/** 팀명으로 로고 이미지 URL 을 조회한다. 매핑이 없거나 팀명이 비면 null. */
	private String resolveTeamImageUrl(String teamName) {
		if (teamName == null || teamName.isBlank()) {
			return null;
		}
		String url = lookupTeamImage(teamName);
		if (url == null) {
			// 라이브 팀명은 "Gen.G Esports"처럼 접미사가 붙는 경우가 있어, 접미사를 떼고 재시도한다(예: DB "Gen.g").
			String stripped = teamName
					.replaceAll("(?i)\\s+(esports club|e-sports|esports|gaming)$", "")
					.trim();
			if (!stripped.isBlank() && !stripped.equalsIgnoreCase(teamName)) {
				url = lookupTeamImage(stripped);
			}
		}
		return url;
	}

	private String lookupTeamImage(String name) {
		return teamRepository.findByNameIgnoreCase(name)
				.map(Team::getImageUrl)
				.orElse(null);
	}

	private LiveGameChampionsResponse.TeamChampions toTeamChampions(
			String teamName, List<LiveParticipantState> participants,
			List<LiveGameChampionsResponse.Ban> bans) {
		List<LiveGameChampionsResponse.Pick> picks = participants.stream()
				.map(this::toPick)
				.toList();
		// 밴은 reconcile 된 배치 데이터에서 채운다(라이브 피드엔 밴이 없음). 없으면 빈 목록.
		return new LiveGameChampionsResponse.TeamChampions(
				teamName, picks, bans, summarize(participants));
	}

	private LiveGameChampionsResponse.Pick toPick(LiveParticipantState p) {
		RuneMetadataResolver.RuneBuild build = runeMetadataResolver.resolveRuneBuild(p.perksJson());
		RuneMetadataResolver.RuneIcons runeIcons = runeMetadataResolver.resolveRuneIcons(p.perksJson());
		ItemMetadataResolver.ItemGroups items = itemMetadataResolver.resolveItemGroups(p.itemIds());
		return new LiveGameChampionsResponse.Pick(
				canonicalPosition(p.role()),
				p.championName(),
				resolveChampionLoadingImageUrl(p.championName()),
				p.playerName(),
				p.level(),
				p.kills(),
				p.deaths(),
				p.assists(),
				p.creepScore(),
				p.totalGoldEarned(),
				p.killParticipation(),
				p.championDamageShare(),
				p.wardsPlaced(),
				p.wardsDestroyed(),
				p.itemImageUrls(),
				items.coreImageUrls(),
				items.questItemImageUrl(),
				items.trinketImageUrl(),
				items.consumableImageUrls(),
				runeIcons.keystoneIconUrl(),
				runeIcons.subStyleIconUrl(),
				toRuneBuild(build));
	}

	private LiveGameChampionsResponse.RuneBuild toRuneBuild(RuneMetadataResolver.RuneBuild build) {
		if (build == null) {
			return null;
		}
		return new LiveGameChampionsResponse.RuneBuild(
				toRuneTree(build.primary()),
				toRuneTree(build.sub()),
				build.shards().stream()
						.map(s -> new LiveGameChampionsResponse.Shard(s.name(), s.iconUrl(), s.label()))
						.toList());
	}

	private LiveGameChampionsResponse.RuneTree toRuneTree(RuneMetadataResolver.RuneTree tree) {
		return new LiveGameChampionsResponse.RuneTree(
				tree.styleName(),
				tree.styleIconUrl(),
				tree.runes().stream()
						.map(r -> new LiveGameChampionsResponse.Rune(r.name(), r.iconUrl(), r.description()))
						.toList());
	}

	/** 팀 헤더 줄(총 KDA · CS · 골드). 참가자 값 합산이라 추가 조회가 없다. */
	private LiveGameChampionsResponse.TeamSummary summarize(List<LiveParticipantState> participants) {
		return new LiveGameChampionsResponse.TeamSummary(
				sum(participants, LiveParticipantState::kills),
				sum(participants, LiveParticipantState::deaths),
				sum(participants, LiveParticipantState::assists),
				sum(participants, LiveParticipantState::creepScore),
				sum(participants, LiveParticipantState::totalGoldEarned));
	}

	private int sum(List<LiveParticipantState> participants,
			java.util.function.Function<LiveParticipantState, Integer> field) {
		return participants.stream()
				.map(field)
				.filter(java.util.Objects::nonNull)
				.mapToInt(Integer::intValue)
				.sum();
	}

	private LiveGameEventsResponse.Event toEvent(
			LiveGameObjectEvent event, LocalDateTime t0, String blueTeamName, String redTeamName) {
		int seconds = elapsedSeconds(t0, event.getSourceFrameTimestampUtc());
		String gameTime = formatGameTime(seconds);
		String type = event.getEventType();

		if (EVENT_KILL.equals(type)) {
			LiveGameEventsResponse.Participant killer = new LiveGameEventsResponse.Participant(
					event.getKillerPlayerName(),
					event.getEventSubType(),
					resolveChampionImageUrl(event.getEventSubType()),
					event.getTeamSide());
			LiveGameEventsResponse.Participant victim = new LiveGameEventsResponse.Participant(
					event.getVictimPlayerName(),
					event.getVictimChampion(),
					resolveChampionImageUrl(event.getVictimChampion()),
					oppositeSide(event.getTeamSide()));
			return new LiveGameEventsResponse.Event(
					type, gameTime, seconds,
					killer, victim, event.getValueAfter(),
					null, null, null, null);
		}

		String teamName = teamNameBySide(event.getTeamSide(), blueTeamName, redTeamName);
		String subType = EVENT_DRAGON.equals(type) ? event.getEventSubType() : null;
		return new LiveGameEventsResponse.Event(
				type, gameTime, seconds,
				null, null, null,
				subType, event.getTeamSide(), teamName, event.getValueAfter());
	}

	private Comparator<LiveParticipantState> pickComparator() {
		return Comparator
				.comparingInt((LiveParticipantState p) -> ROLE_ORDER.getOrDefault(canonicalPosition(p.role()), 99))
				.thenComparing(p -> p.participantId() == null ? Integer.MAX_VALUE : p.participantId());
	}

	/** 라이브 역할 문자열을 계약상의 position 값(top/jungle/mid/bottom/support)으로 정규화한다. */
	private String canonicalPosition(String role) {
		if (role == null || role.isBlank()) {
			return null;
		}
		return switch (role.trim().toLowerCase(Locale.ROOT)) {
			case "top" -> "top";
			case "jungle", "jungler", "jng" -> "jungle";
			case "middle", "mid" -> "mid";
			case "bottom", "bot", "adc" -> "bottom";
			case "support", "sup", "utility" -> "support";
			default -> role.trim().toLowerCase(Locale.ROOT);
		};
	}

	/** 영문 챔피언명으로 정사각 아이콘 URL 을 조회한다. 작은 아이콘(킬 피드 등)용. 매핑 없으면 null (Flutter 가 Data Dragon 폴백). */
	private String resolveChampionImageUrl(String championName) {
		if (championName == null || championName.isBlank()) {
			return null;
		}
		String normalized = NameNormalizer.normalizeChampionName(championName);
		return championRepository.findByChampionNameEn(normalized)
				.map(Champion::getImageUrl)
				.orElse(null);
	}

	/**
	 * 챔피언 픽 카드(세로)용 고화질 로딩 이미지 URL. loading_image_url(CommunityDragon centered splash)이
	 * 비면 정사각 아이콘으로 폴백한다. 정사각을 세로로 늘리면 화질이 저하되므로 픽에는 이쪽을 쓴다.
	 */
	private String resolveChampionLoadingImageUrl(String championName) {
		if (championName == null || championName.isBlank()) {
			return null;
		}
		String normalized = NameNormalizer.normalizeChampionName(championName);
		return championRepository.findByChampionNameEn(normalized)
				.map(c -> c.getLoadingImageUrl() != null && !c.getLoadingImageUrl().isBlank()
						? c.getLoadingImageUrl()
						: c.getImageUrl())
				.orElse(null);
	}

	private int elapsedSeconds(LocalDateTime t0, LocalDateTime frame) {
		if (t0 == null || frame == null) {
			return 0;
		}
		long seconds = frame.toEpochSecond(ZoneOffset.UTC) - t0.toEpochSecond(ZoneOffset.UTC);
		return (int) Math.max(0, seconds);
	}

	private String formatGameTime(int totalSeconds) {
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		return String.format("%02d:%02d", minutes, seconds);
	}

	private String oppositeSide(String teamSide) {
		if (BLUE.equalsIgnoreCase(teamSide)) {
			return RED;
		}
		if (RED.equalsIgnoreCase(teamSide)) {
			return BLUE;
		}
		return null;
	}

	private String teamNameBySide(String teamSide, String blueTeamName, String redTeamName) {
		if (BLUE.equalsIgnoreCase(teamSide)) {
			return blueTeamName;
		}
		if (RED.equalsIgnoreCase(teamSide)) {
			return redTeamName;
		}
		return null;
	}

	private LiveGameState requireState(String gameId) {
		return liveStateQueryService.getLatestState(gameId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "라이브 경기 정보를 찾을 수 없습니다."));
	}
}
