package com.toy.nar.app.mobile.match;

import com.toy.nar.app.lolesports.live.LiveStateQueryService;
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

		return new LiveGameChampionsResponse(
				gameId,
				toTeamChampions(state.blueTeamName(), blue,
						bansBySide.getOrDefault(BLUE_SIDE_KEY, List.of())),
				toTeamChampions(state.redTeamName(), red,
						bansBySide.getOrDefault(RED_SIDE_KEY, List.of())));
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
				.map(p -> new LiveGameChampionsResponse.Pick(
						canonicalPosition(p.role()),
						p.championName(),
						resolveChampionImageUrl(p.championName()),
						p.playerName()))
				.toList();
		// 밴은 reconcile 된 배치 데이터에서 채운다(라이브 피드엔 밴이 없음). 없으면 빈 목록.
		return new LiveGameChampionsResponse.TeamChampions(teamName, picks, bans);
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

	/** 영문 챔피언명으로 이미지 URL 을 조회한다. 매핑이 없으면 null (Flutter 가 Data Dragon 으로 폴백). */
	private String resolveChampionImageUrl(String championName) {
		if (championName == null || championName.isBlank()) {
			return null;
		}
		String normalized = NameNormalizer.normalizeChampionName(championName);
		return championRepository.findByChampionNameEn(normalized)
				.map(Champion::getImageUrl)
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
