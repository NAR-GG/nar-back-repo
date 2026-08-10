package com.toy.nar.app.lolesports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.repository.LeagueMatchGame;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.common.util.NameNormalizer;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameExternalIdentity;
import com.toy.nar.domain.game.repository.GameExternalIdentityRepository;
import com.toy.nar.domain.game.repository.GameRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.entity.TeamExternalIdentity;
import com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueMatchService {

	private static final String LOLESPORTS_SOURCE = "LOLESPORTS";
	/** 미래(newer) 페이지 추적 상한. 실측상 리그당 1홉이면 끝나지만 시즌 편성이 길어질 때를 대비한 안전장치다. */
	private static final int MAX_NEWER_PAGE_HOPS = 5;
	private static final Map<String, String> TEAM_ALIAS_TARGETS_BY_EXTERNAL_ID = Map.of(
			"107700204561086446", "Deep Cross Gaming",
			"99566406332987990", "Chiefs Esports Club",
			"101428372605353526", "Qt Dig∞",
			"99566408221961358", "Red Canids",
			"109480204628225868", "Los Grandes",
			"107598699275015260", "Leviatan",
			"109480056092207899", "Fluxo W7m",
			"99566408221114231", "Kabum! Ilha Das Lendas");
	private static final Set<String> AUTO_CREATE_EXTERNAL_TEAM_IDS = Set.of(
			"106972778172351142", // NRG Kia
			"98767991930907107", // Immortals Progressive
			"99566408222831088", // Liberty
			"99566408217116828", // INTZ
			"99294153824386385", // Golden Guardians
			"98767991935149427" // Movistar R7
	);
	private static final Map<String, Set<String>> GAME_LEAGUE_ALIASES = Map.of(
			"WORLDS", Set.of("WORLDS", "WLDS"));

	private final LeagueMatchRepository leagueMatchRepository;
	private final LeagueMatchGameRepository leagueMatchGameRepository;
	private final com.toy.nar.app.lolesports.season.LeagueSeasonResolver leagueSeasonResolver;
	private final com.toy.nar.domain.participant.repository.TeamRepository teamRepository;
	private final TeamExternalIdentityRepository teamExternalIdentityRepository;
	private final GameExternalIdentityRepository gameExternalIdentityRepository;
	private final WorldsService worldsService;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;
	private final GameRepository gameRepository;
	private final NaverEsportsScoreClient naverEsportsScoreClient;
	private final com.toy.nar.app.schedule.CacheEvictionService cacheEvictionService;

	// [Scheduler용] 특정 리그의 최신 경기를 가져와 DB에 저장 (1페이지)
	public void syncMatches(String leagueSlug) {
		syncMatches(leagueSlug, true);
	}

	// 스케줄러 경로에서는 팀 메타데이터 동기화를 제외해 DB 트래픽을 줄인다.
	public void syncMatchesWithoutTeamMetadata(String leagueSlug) {
		syncMatches(leagueSlug, false);
	}

	/** [Scheduler용] 주어진 기간에 경기가 있는 리그명 목록(중복 제거). 라이브 디스커버리 대상을 좁힌다. */
	public List<String> findLeaguesWithMatchesBetween(LocalDateTime start, LocalDateTime end) {
		return leagueMatchRepository.findDistinctLeagueNamesByDateRange(start, end);
	}

	public boolean syncRealtimeMatchStatus(MatchResultDto match, String fallbackLeagueSlug) {
		if (match == null || match.getMatchId() == null || match.getMatchId().isBlank()) {
			return false;
		}
		String leagueSlug = match.getLeagueName() == null || match.getLeagueName().isBlank()
				? fallbackLeagueSlug
				: match.getLeagueName();
		if ("inProgress".equalsIgnoreCase(match.getState())) {
			overlayNaverScoreIfAhead(match);
		}
		MatchSyncUpsertResult result = upsertLeagueMatches(leagueSlug, List.of(match));
		boolean changed = result.insertedMatches() > 0 || result.updatedMatches() > 0;
		if (changed) {
			log.info("Realtime match status synced. matchId={} state={} score={}:{}",
					match.getMatchId(),
					match.getState(),
					match.getBlueTeam() == null ? null : match.getBlueTeam().getWins(),
					match.getRedTeam() == null ? null : match.getRedTeam().getWins());
		}
		return changed;
	}

	/**
	 * livestats 프레임이 종료(finished)를 알리는데 업스트림 state 는 여전히 unstarted 인 구간에서,
	 * 네이버가 매치 종료(matchStatus=RESULT)를 확인해주면 completed 로 확정한다.
	 *
	 * <p>업스트림 completed flip 은 실측 17분+ 늦게 온다(2026-07-27 KESPA T1 vs DNS: 종료 21:40:12,
	 * flip 21:57:37). 그 구간을 네이버로 메꾼다. 세트 사이(네이버가 아직 RESULT 아님)면 false 를
	 * 돌려 호출측이 기존대로 스킵하게 한다 — 업스트림 unstarted 를 그대로 쓰면 DB 가 되돌아간다.</p>
	 *
	 * @return DB 가 실제로 바뀌었으면 true (종료 미확정·스코어 없음·이미 최신이면 false)
	 */
	public boolean syncCompletedMatchFromNaver(MatchResultDto match, String fallbackLeagueSlug) {
		if (match == null || match.getBlueTeam() == null || match.getRedTeam() == null
				|| match.getMatchDate() == null) {
			return false;
		}
		LocalDateTime matchDateUtc;
		try {
			matchDateUtc = LocalDateTime.parse(match.getMatchDate(), DateTimeFormatter.ISO_DATE_TIME);
		} catch (Exception e) {
			return false;
		}
		NaverEsportsScoreClient.Result naver = naverEsportsScoreClient.fetchResult(
				match.getBlueTeam().getCode(), match.getRedTeam().getCode(), matchDateUtc);
		if (naver == null || naver.score() == null) {
			return false;
		}
		// 종료 확정과 별개로, 이미 받아온 네이버 스코어가 DB 보다 앞서면 스코어만 먼저 반영한다.
		// 세트 사이 KESPA 는 업스트림 state 가 unstarted 로 방치돼 디스커버리가 realtime sync
		// (네이버 오버레이 포함)에 도달하지 못하고 매 사이클 이 메서드에서만 돈다 — 여기서
		// 버리면 다음 세트 픽밴의 Riot flip 까지 스코어가 고착된다(실측 2026-08-10 DNS vs GEN:
		// 세트1 종료 후 20분간 0:0).
		LeagueMatch existing = leagueMatchRepository.findById(match.getMatchId()).orElse(null);
		overlayScoreOnlyIfAhead(existing, naver.score());
		if (!naver.finished() || naver.score()[0] + naver.score()[1] == 0) {
			return false;
		}
		// 네이버 RESULT 만으로는 매치 종료를 단정할 수 없다 — KESPA 는 네이버가 RESULT 플래그를
		// 매치 중간에 미리 세운다(실측 2026-08-10 GEN vs HLE: 세트1 종료 직후 RESULT+진행 스코어
		// 1:0 → bo3 가 1:0 completed 로 고착, 이후 추적까지 게이트에 막혀 세트2·3 무음).
		// 다전제 승리 조건에 실제로 도달한 스코어만 종료로 받아들인다.
		// bestOf 는 업스트림 DTO 가 간헐적으로 비워 보내므로 DB 값으로 폴백한다 — 폴백까지
		// 미상이면 확정하지 않는다. 늦더라도(업스트림 flip 대기) 틀리는 것보단 낫다.
		Integer bestOf = match.getBestOf() != null
				? match.getBestOf()
				: existing == null ? null : existing.getBestOf();
		if (!reachesMatchWin(bestOf, naver.score()[0], naver.score()[1])) {
			log.info("Naver RESULT 를 다전제 미완으로 무시. matchId={} bestOf={} score={}:{}",
					match.getMatchId(), bestOf, naver.score()[0], naver.score()[1]);
			return false;
		}
		match.getBlueTeam().setWins(naver.score()[0]);
		match.getRedTeam().setWins(naver.score()[1]);
		match.setState("completed");
		String leagueSlug = match.getLeagueName() == null || match.getLeagueName().isBlank()
				? fallbackLeagueSlug
				: match.getLeagueName();
		MatchSyncUpsertResult result = upsertLeagueMatches(leagueSlug, List.of(match));
		boolean changed = result.insertedMatches() > 0 || result.updatedMatches() > 0;
		if (changed) {
			log.info("Naver 종료 확정으로 매치 completed 반영. matchId={} score={}:{}",
					match.getMatchId(), naver.score()[0], naver.score()[1]);
		}
		return changed;
	}

	/** 다전제 승리 조건 도달 여부. LivePollingScheduler.isMatchEnded 와 같은 판정 — bestOf 미상은 false. */
	public static boolean reachesMatchWin(Integer bestOf, int blueScore, int redScore) {
		if (bestOf == null || bestOf < 1) {
			return false;
		}
		return Math.max(blueScore, redScore) >= bestOf / 2 + 1;
	}

	/**
	 * 종료 확정을 보류하더라도 네이버 스코어가 DB 보다 앞서면 스코어만 선반영한다.
	 * state 는 건드리지 않는다 — 되돌림 클래스(#354/#355)와 무관하게 유지.
	 * 실패해도 종료 확정 흐름을 깨면 안 되므로 흡수한다.
	 */
	private void overlayScoreOnlyIfAhead(LeagueMatch existing, int[] naverScore) {
		try {
			int[] ahead = scoreOnlyOverlay(existing, naverScore);
			if (ahead == null) {
				return;
			}
			existing.applyScore(ahead[0], ahead[1], LocalDateTime.now());
			existing.applySetWinners(advanceSetWinners(
					existing.getSetWinners(), ahead[0], ahead[1], false));
			leagueMatchRepository.save(existing);
			cacheEvictionService.evictScheduleCaches();
			log.info("세트 사이 네이버 스코어 선반영. matchId={} score={}:{}",
					existing.getId(), ahead[0], ahead[1]);
		} catch (Exception e) {
			log.warn("네이버 스코어 선반영 실패. matchId={}",
					existing == null ? null : existing.getId(), e);
		}
	}

	/**
	 * 스코어 선반영 판정. 네이버 합이 DB 합보다 앞설 때만 그 스코어, 아니면 null.
	 * 완료된 경기는 대상이 아니다 — 완료 스코어는 Riot 최종값이 진실이고,
	 * 네이버 wrong-high 가 확정 결과를 덮으면 안 된다.
	 */
	static int[] scoreOnlyOverlay(LeagueMatch existing, int[] naverScore) {
		if (existing == null || naverScore == null || isCompleted(existing)) {
			return null;
		}
		int blue = existing.getBlueScore() == null ? 0 : existing.getBlueScore();
		int red = existing.getRedScore() == null ? 0 : existing.getRedScore();
		return pickAheadScore(naverScore, blue, red);
	}

	/**
	 * 라이브 중 Riot gameWins 는 다음 세트 픽밴에야 뒤집혀 몇 분간 stale 이다. 네이버 e스포츠는
	 * 세트 종료 직후 반영되므로, 진행 중 경기의 스코어 합이 네이버에서 더 앞서면 그 값으로 덮어쓴다.
	 * 리스트·상세·모바일이 모두 이 DB 스코어를 읽으므로 표시 지연이 함께 줄어든다.
	 * 네이버 매칭 검증(팀코드·gameCode=lol·시작시각±6h)과 킬스위치는 클라이언트가 담당 — null 이면 Riot 유지.
	 * ponytail: 상한 없음. 네이버 wrong-high 는 completed flip 때 Riot 최종값이 덮어써 self-heal.
	 * false-high 관측되면 네이버 maxMatchCount 가드 추가.
	 */
	private void overlayNaverScoreIfAhead(MatchResultDto match) {
		if (match.getBlueTeam() == null || match.getRedTeam() == null || match.getMatchDate() == null) {
			return;
		}
		LocalDateTime matchDateUtc;
		try {
			matchDateUtc = LocalDateTime.parse(match.getMatchDate(), DateTimeFormatter.ISO_DATE_TIME);
		} catch (Exception e) {
			return;
		}
		int[] naver = naverEsportsScoreClient.fetchScore(
				match.getBlueTeam().getCode(), match.getRedTeam().getCode(), matchDateUtc);
		int[] ahead = pickAheadScore(naver, match.getBlueTeam().getWins(), match.getRedTeam().getWins());
		if (ahead != null) {
			match.getBlueTeam().setWins(ahead[0]);
			match.getRedTeam().setWins(ahead[1]);
		}
	}

	/** 네이버 스코어 합이 Riot 보다 앞서면 [blue, red] 반환, 아니면(같거나 뒤짐·null) null → Riot 유지. */
	static int[] pickAheadScore(int[] naver, int riotBlue, int riotRed) {
		if (naver == null) {
			return null;
		}
		return naver[0] + naver[1] > riotBlue + riotRed ? naver : null;
	}

	/**
	 * 스코어 전이로 세트별 승자 목록을 전진시킨다. 업스트림은 세트별 승자를 주지 않으므로
	 * (getEventDetails/getGames/getCompletedEvents 전수 실측 — games[].teams 는 side 뿐)
	 * 스코어가 +1 되는 순간 그 세트의 승자를 적는 것이 유일한 실시간 소스다.
	 *
	 * <p>형식: 콤마 구분, index = 세트 번호. B/R = 매치 blue/red, '?' = 순서 미상.
	 * 한 사이클에 두 팀이 함께 오르면(동기화 공백 30분+) 순서를 알 수 없어 '?' 로 채워
	 * 이후 세트의 인덱스를 지킨다. 스코어가 목록보다 후퇴하면(네이버 wrong-high 정정·리메이크)
	 * 목록을 재구축한다 — 완봉이면 전부 귀속, 아니면 '?'.</p>
	 */
	static String advanceSetWinners(String current, Integer blueScore, Integer redScore, boolean completed) {
		int blue = blueScore == null ? 0 : blueScore;
		int red = redScore == null ? 0 : redScore;
		int total = blue + red;
		if (total <= 0) {
			return current;
		}

		List<String> winners = current == null || current.isBlank()
				? new ArrayList<>()
				: new ArrayList<>(Arrays.asList(current.split(",")));
		long knownBlue = winners.stream().filter("B"::equals).count();
		long knownRed = winners.stream().filter("R"::equals).count();

		// 스코어 후퇴 — 진행 중엔 업스트림 stale(30분 sync 가 네이버 오버레이보다 뒤짐)일 가능성이
		// 커서 기존 귀속을 지키고, 최종 스코어(completed)만 재구축 근거로 믿는다.
		if (blue < knownBlue || red < knownRed || total < winners.size()) {
			return completed ? rebuildSetWinners(blue, red) : current;
		}

		// 한쪽이 0 이면 지나간 세트 전부가 상대 승 — '?' 로 남았던 세트도 소급 확정된다.
		if (blue == 0 || red == 0) {
			return rebuildSetWinners(blue, red);
		}
		if (total == winners.size()) {
			return current;
		}

		int added = total - winners.size();
		long addedBlue = blue - knownBlue;
		long addedRed = red - knownRed;
		String mark = addedBlue == added && addedRed == 0 ? "B"
				: addedRed == added && addedBlue == 0 ? "R"
				: "?";
		for (int i = 0; i < added; i++) {
			winners.add(mark);
		}
		return String.join(",", winners);
	}

	/**
	 * 업스트림이 이미 끝난 경기를 결과 없는 상태로 되돌려 보내는지.
	 *
	 * <p>KESPA·EWC 는 경기가 끝나도 state 를 unstarted, 스코어를 0:0 으로 방치한다. 30분 주기
	 * 전체 동기화는 그 값을 조건 없이 덮어써서 이미 확정한 결과를 지운다. 2026-08-04 KESPA
	 * T1 vs HLE 가 18:02 에 네이버로 2:1 completed 확정된 뒤 이 경로로 0:0 unstarted 가 됐다.
	 * 디스커버리의 재확정은 matchId 당 1회라 되돌려진 뒤에는 스스로 복구되지 않는다.</p>
	 *
	 * <p>결과가 있는 completed 를 completed 가 아닌 상태로 되돌리는 것은 전부 막는다.
	 * 처음에는 스코어 합 0 만 막았는데, 그것으로는 마지막 세트 구간을 못 잡는다 — 업스트림
	 * gameWins 는 마지막 세트에서 25분+ stale 이라 2:0 으로 끝난 경기가 0:0 이 아니라
	 * {@code inProgress 1:0} 으로 들어온다. 네이버로 먼저 확정한 completed 가 그 값에 덮여
	 * "종료 → 진행중 → 종료" 로 튄다.</p>
	 *
	 * <p>업스트림도 종료로 보는 갱신({@code incoming} 이 completed)은 그대로 통과시킨다 —
	 * 리메이크·오기 정정이 이 경로로 들어오고, 업스트림 flip 이 도착했을 때 최종 스코어로
	 * 덮어쓰는 self-heal 도 여기에 의존한다.</p>
	 */
	static boolean isResultRegression(LeagueMatch existing, LeagueMatch incoming) {
		if (!isCompleted(existing) || scoreSum(existing) <= 0) {
			return false;
		}
		return !isCompleted(incoming);
	}

	private static int scoreSum(LeagueMatch match) {
		int blue = match.getBlueScore() == null ? 0 : match.getBlueScore();
		int red = match.getRedScore() == null ? 0 : match.getRedScore();
		return blue + red;
	}

	private static boolean isCompleted(LeagueMatch match) {
		return "completed".equalsIgnoreCase(match.getState());
	}

	private static String rebuildSetWinners(int blue, int red) {
		String mark = red == 0 ? "B" : blue == 0 ? "R" : "?";
		List<String> winners = new ArrayList<>();
		for (int i = 0; i < blue + red; i++) {
			winners.add(mark);
		}
		return String.join(",", winners);
	}

	public void syncMatches(String leagueSlug, boolean includeTeamMetadataSync) {
		log.info("Starting sync for league: {}", leagueSlug);
		// 1. 외부 API에서 데이터 가져오기 (1페이지 분량, pageToken=null)
		MatchResponseWrapper response = worldsService.getWorldsMatches(null, leagueSlug);
		List<MatchResultDto> matches = response.getMatches();

		if (matches.isEmpty()) {
			log.info("No matches found for league: {}", leagueSlug);
			return;
		}

		MatchSyncUpsertResult upsertResult = upsertLeagueMatches(leagueSlug, matches);

		if (includeTeamMetadataSync) {
			int metadataUpdated = updateTeamMetadataFromMatches(matches);
			log.info("Team metadata sync completed during syncMatches. updated={}", metadataUpdated);
		}

		autoBackfillRecentMatches(leagueSlug, matches, upsertResult.dirtyMatchIds());

		log.info("Synced {} matches for league: {} (inserted={}, updated={}, skipped={})",
				matches.size(), leagueSlug, upsertResult.insertedMatches(), upsertResult.updatedMatches(),
				upsertResult.skippedMatches());

		syncNewerPages(leagueSlug, response.getNewerPageToken());
	}

	/**
	 * 기본 페이지 창 밖의 미래 경기를 따라간다.
	 *
	 * <p>기본 페이지는 과거~가까운 미래까지만 담는다. getSchedule 은 커서 페이지네이션이고 기존 sync 는 {@code pages.older} 만 따라갔다.
	 * 그래서 창 밖 미래 일정(LCK 플레이오프·결승, LPL 정규 잔여+플레이오프 등)이 DB 에 아예 없었다.
	 * 여기서는 upsert 만 한다 — 아직 시작도 안 한 경기에는 매핑할 게임이 없어
	 * autoBackfill 을 태우면 매 sync 마다 헛된 getEventDetails 호출만 쌓인다.</p>
	 */
	private void syncNewerPages(String leagueSlug, String startToken) {
		String token = startToken;
		Set<String> visitedTokens = new java.util.LinkedHashSet<>();
		int hops = 0;

		while (token != null && !token.isBlank() && hops < MAX_NEWER_PAGE_HOPS && visitedTokens.add(token)) {
			hops++;
			try {
				MatchResponseWrapper page = worldsService.getWorldsMatches(token, leagueSlug);
				List<MatchResultDto> matches = page.getMatches();
				if (matches == null || matches.isEmpty()) {
					break;
				}
				MatchSyncUpsertResult result = upsertLeagueMatches(leagueSlug, matches);
				log.info("Synced newer page {} for league: {} ({} matches, inserted={}, updated={}, skipped={})",
						hops, leagueSlug, matches.size(), result.insertedMatches(), result.updatedMatches(),
						result.skippedMatches());
				token = page.getNewerPageToken();
			} catch (Exception e) {
				log.warn("Failed to sync newer page {} for league={}: {}", hops, leagueSlug, e.getMessage());
				return;
			}
		}
		if (hops >= MAX_NEWER_PAGE_HOPS) {
			log.warn("Newer page hop limit reached for league={} (limit={}) — 남은 미래 페이지는 다음 sync 에서 따라간다.",
					leagueSlug, MAX_NEWER_PAGE_HOPS);
		}
	}

	public RecentMatchBackfillResult backfillRecentMatches(String leagueSlug, boolean includeTeamMetadataSync) {
		String normalizedLeague = leagueSlug == null ? "" : leagueSlug.trim().toUpperCase();
		log.info("Manual recent match backfill requested for league={}, includeTeamMetadata={}",
				normalizedLeague, includeTeamMetadataSync);

		MatchResponseWrapper response = worldsService.getWorldsMatches(null, normalizedLeague);
		List<MatchResultDto> matches = response.getMatches();
		if (matches == null || matches.isEmpty()) {
			return new RecentMatchBackfillResult(
					normalizedLeague,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					0,
					includeTeamMetadataSync);
		}

		MatchSyncUpsertResult upsertResult = upsertLeagueMatches(normalizedLeague, matches);
		int metadataUpdated = 0;
		if (includeTeamMetadataSync) {
			metadataUpdated = updateTeamMetadataFromMatches(matches);
		}
		AutoBackfillRecentMatchesResult autoResult = autoBackfillRecentMatches(
				normalizedLeague,
				matches,
				upsertResult.dirtyMatchIds());

		return new RecentMatchBackfillResult(
				normalizedLeague,
				matches.size(),
				upsertResult.insertedMatches(),
				upsertResult.updatedMatches(),
				upsertResult.skippedMatches(),
				metadataUpdated,
				autoResult.pageMatchCount(),
				autoResult.teamIdentityResult().createdMappings(),
				autoResult.teamIdentityResult().updatedMappings(),
				autoResult.teamIdentityResult().unresolvedMappings(),
				autoResult.gameIdSyncResult().updatedMatches(),
				autoResult.gameIdSyncResult().unchangedMatches(),
				autoResult.gameIdSyncResult().failedMatches(),
				autoResult.gameIdentityResult().createdMappings(),
				autoResult.gameIdentityResult().updatedMappings(),
				autoResult.gameIdentityResult().unresolvedMappings(),
				includeTeamMetadataSync);
	}

	// 팀 메타데이터 동기화는 일 배치 경로에서만 실행
	public int syncTeamMetadataForLeagues(List<String> leagues) {
		if (leagues == null || leagues.isEmpty()) {
			return 0;
		}

		int totalUpdated = 0;
		for (String league : leagues) {
			try {
				MatchResponseWrapper response = worldsService.getWorldsMatches(null, league);
				List<MatchResultDto> matches = response.getMatches();
				if (matches == null || matches.isEmpty()) {
					continue;
				}
				totalUpdated += updateTeamMetadataFromMatches(matches);
			} catch (Exception e) {
				log.warn("Team metadata batch failed for league={}: {}", league, e.getMessage());
			}
		}
		return totalUpdated;
	}

	@Transactional
	public TeamIdentityBackfillResult backfillTeamExternalIdentities(List<String> leagues) {
		return backfillTeamExternalIdentities(leagues, false, 1);
	}

	@Transactional
	public TeamIdentityBackfillResult backfillTeamExternalIdentities(List<String> leagues, boolean fullHistory,
			int maxPages) {
		List<String> targetLeagues = (leagues == null || leagues.isEmpty()) ? LeagueConstants.TARGET_LEAGUES : leagues;
		Map<String, ExternalTeamCandidate> candidatesByExternalId = new LinkedHashMap<>();
		int fetchedPages = 0;

		for (String league : targetLeagues) {
			try {
				String pageToken = null;
				int leaguePageCount = 0;

				while (true) {
					MatchResponseWrapper response = worldsService.getWorldsMatches(pageToken, league);
					leaguePageCount++;
					fetchedPages++;

					List<MatchResultDto> matches = response.getMatches();
					if (matches == null || matches.isEmpty()) {
						break;
					}
					for (MatchResultDto match : matches) {
						collectExternalTeamCandidate(candidatesByExternalId, league, match.getBlueTeam());
						collectExternalTeamCandidate(candidatesByExternalId, league, match.getRedTeam());
					}

					if (!fullHistory) {
						break;
					}

					pageToken = response.getNextPageToken();
					if (pageToken == null || pageToken.isBlank()) {
						break;
					}
					if (maxPages > 0 && leaguePageCount >= maxPages) {
						log.info("Stopping team identity history backfill at configured maxPages={} for league={}",
								maxPages, league);
						break;
					}
					Thread.sleep(250);
				}
			} catch (Exception e) {
				log.warn("Team identity backfill source fetch failed for league={}: {}", league, e.getMessage());
			}
		}

		return upsertTeamExternalIdentities(targetLeagues, fetchedPages, candidatesByExternalId);
	}

	private TeamIdentityBackfillResult syncTeamExternalIdentitiesFromMatches(String league, List<MatchResultDto> matches) {
		if (matches == null || matches.isEmpty()) {
			return new TeamIdentityBackfillResult(List.of(league), 0, 0, 0, 0, 0, 0);
		}

		Map<String, ExternalTeamCandidate> candidatesByExternalId = new LinkedHashMap<>();
		for (MatchResultDto match : matches) {
			collectExternalTeamCandidate(candidatesByExternalId, league, match.getBlueTeam());
			collectExternalTeamCandidate(candidatesByExternalId, league, match.getRedTeam());
		}

		return upsertTeamExternalIdentities(List.of(league), 0, candidatesByExternalId);
	}

	private TeamIdentityBackfillResult upsertTeamExternalIdentities(
			List<String> targetLeagues,
			int fetchedPages,
			Map<String, ExternalTeamCandidate> candidatesByExternalId) {
		if (candidatesByExternalId.isEmpty()) {
			return new TeamIdentityBackfillResult(targetLeagues, fetchedPages, 0, 0, 0, 0, 0);
		}

		List<Team> allTeams = teamRepository.findAll();
		Map<String, Team> teamsByExactName = buildExactTeamNameMap(allTeams);
		Map<String, Team> normalizedTeamsByName = buildNormalizedTeamNameMap(allTeams);
		Map<String, Team> teamsByCode = buildTeamCodeMap(allTeams);
		Map<String, TeamExternalIdentity> existingByExternalId = teamExternalIdentityRepository
				.findBySourceAndExternalTeamIdIn(LOLESPORTS_SOURCE, candidatesByExternalId.keySet()).stream()
				.collect(Collectors.toMap(TeamExternalIdentity::getExternalTeamId, identity -> identity));

		int created = 0;
		int updated = 0;
		int unresolved = 0;
		int conflicts = 0;
		List<TeamExternalIdentity> dirtyIdentities = new ArrayList<>();

		for (ExternalTeamCandidate candidate : candidatesByExternalId.values()) {
			Team resolvedTeam = resolveTeamCandidate(candidate, teamsByExactName, normalizedTeamsByName, teamsByCode);
			if (resolvedTeam == null && shouldAutoCreateTeam(candidate)) {
				resolvedTeam = createMissingTeam(candidate);
				allTeams.add(resolvedTeam);
				teamsByExactName.put(resolvedTeam.getName(), resolvedTeam);
				normalizedTeamsByName.put(NameNormalizer.normalizeTeamName(resolvedTeam.getName()), resolvedTeam);
				if (resolvedTeam.getCode() != null && !resolvedTeam.getCode().isBlank()) {
					teamsByCode.put(resolvedTeam.getCode().trim().toUpperCase(), resolvedTeam);
				}
				log.info("Created missing internal team for externalTeamId={} league={} name={}",
						candidate.externalTeamId(), candidate.league(), candidate.externalName());
			}
			TeamExternalIdentity existingIdentity = existingByExternalId.get(candidate.externalTeamId());

			if (existingIdentity != null) {
				if (resolvedTeam != null && !existingIdentity.getTeam().getId().equals(resolvedTeam.getId())) {
					conflicts++;
					log.warn(
							"Team identity conflict: externalTeamId={} existingTeam={} resolvedTeam={} league={} name={}",
							candidate.externalTeamId(),
							existingIdentity.getTeam().getName(),
							resolvedTeam.getName(),
							candidate.league(),
							candidate.externalName());
					continue;
				}

				String matchedBy = resolvedTeam != null ? determineMatchedBy(candidate, resolvedTeam)
						: existingIdentity.getMatchedBy();
				if (hasIdentityMetadataChange(existingIdentity, candidate, matchedBy)) {
					existingIdentity.updateMatchMetadata(candidate.externalName(), matchedBy, confidenceFor(matchedBy));
					dirtyIdentities.add(existingIdentity);
					updated++;
				}
				continue;
			}

			if (resolvedTeam == null) {
				unresolved++;
				log.info("Team identity unresolved: externalTeamId={} league={} name={} code={}",
						candidate.externalTeamId(), candidate.league(), candidate.externalName(),
						candidate.externalCode());
				continue;
			}

			String matchedBy = determineMatchedBy(candidate, resolvedTeam);
			dirtyIdentities.add(TeamExternalIdentity.builder()
					.source(LOLESPORTS_SOURCE)
					.externalTeamId(candidate.externalTeamId())
					.team(resolvedTeam)
					.externalNameRaw(candidate.externalName())
					.matchedBy(matchedBy)
					.confidence(confidenceFor(matchedBy))
					.build());
			created++;
		}

		if (!dirtyIdentities.isEmpty()) {
			teamExternalIdentityRepository.saveAll(dirtyIdentities);
		}

		return new TeamIdentityBackfillResult(
				targetLeagues,
				fetchedPages,
				candidatesByExternalId.size(),
				created,
				updated,
				unresolved,
				conflicts);
	}

	@Transactional
	public LeagueMatchExternalTeamIdBackfillResult backfillLeagueMatchExternalTeamIds(List<String> leagues) {
		List<String> targetLeagues = (leagues == null || leagues.isEmpty()) ? LeagueConstants.TARGET_LEAGUES : leagues;
		List<LeagueMatch> matches = leagueMatchRepository.findAll().stream()
				.filter(match -> match.getLeagueName() != null
						&& targetLeagues.contains(match.getLeagueName().toUpperCase()))
				.toList();

		List<TeamExternalIdentity> identities = teamExternalIdentityRepository.findAll().stream()
				.filter(identity -> LOLESPORTS_SOURCE.equals(identity.getSource()))
				.toList();

		Map<String, String> externalIdByExactExternalName = buildUniqueExternalIdMap(
				identities,
				identity -> identity.getExternalNameRaw(),
				false);
		Map<String, String> externalIdByNormalizedExternalName = buildUniqueExternalIdMap(
				identities,
				identity -> identity.getExternalNameRaw(),
				true);
		Map<String, String> externalIdByExactInternalName = buildUniqueExternalIdMap(
				identities,
				identity -> identity.getTeam().getName(),
				false);
		Map<String, String> externalIdByNormalizedInternalName = buildUniqueExternalIdMap(
				identities,
				identity -> identity.getTeam().getName(),
				true);
		Map<String, String> externalIdByCode = buildUniqueCodeToExternalIdMap(identities);

		int updated = 0;
		int unresolved = 0;
		List<LeagueMatch> dirtyMatches = new ArrayList<>();

		for (LeagueMatch match : matches) {
			String resolvedBlueExternalTeamId = resolveLeagueMatchExternalTeamId(
					match.getBlueTeamName(),
					match.getBlueTeamCode(),
					externalIdByExactExternalName,
					externalIdByNormalizedExternalName,
					externalIdByExactInternalName,
					externalIdByNormalizedInternalName,
					externalIdByCode);
			String resolvedRedExternalTeamId = resolveLeagueMatchExternalTeamId(
					match.getRedTeamName(),
					match.getRedTeamCode(),
					externalIdByExactExternalName,
					externalIdByNormalizedExternalName,
					externalIdByExactInternalName,
					externalIdByNormalizedInternalName,
					externalIdByCode);

			boolean unresolvedBlue = resolvedBlueExternalTeamId == null
					&& !isPlaceholderTeamName(match.getBlueTeamName());
			boolean unresolvedRed = resolvedRedExternalTeamId == null && !isPlaceholderTeamName(match.getRedTeamName());
			if (unresolvedBlue || unresolvedRed) {
				unresolved++;
			}

			boolean changed = !java.util.Objects.equals(match.getBlueExternalTeamId(), resolvedBlueExternalTeamId)
					|| !java.util.Objects.equals(match.getRedExternalTeamId(), resolvedRedExternalTeamId);
			if (changed) {
				match.updateExternalTeamIds(resolvedBlueExternalTeamId, resolvedRedExternalTeamId);
				dirtyMatches.add(match);
				updated++;
			}
		}

		if (!dirtyMatches.isEmpty()) {
			leagueMatchRepository.saveAll(dirtyMatches);
		}

		return new LeagueMatchExternalTeamIdBackfillResult(targetLeagues, matches.size(), updated, unresolved);
	}

	public GameExternalIdentityBackfillResult backfillGameExternalIdentities(List<String> leagues, int year) {
		List<String> targetLeagues = (leagues == null || leagues.isEmpty()) ? LeagueConstants.TARGET_LEAGUES : leagues;
		LocalDateTime start = java.time.LocalDate.of(year, 1, 1).atStartOfDay();
		LocalDateTime end = java.time.LocalDate.of(year + 1, 1, 1).atStartOfDay().minusNanos(1);
		List<LeagueMatch> matches = leagueMatchRepository.findByDateRange(start, end).stream()
				.filter(match -> match.getLeagueName() != null
						&& targetLeagues.contains(match.getLeagueName().toUpperCase()))
				.filter(match -> match.getMatchDate() != null)
				.filter(match -> !isPlaceholderTeamName(match.getBlueTeamName())
						&& !isPlaceholderTeamName(match.getRedTeamName()))
				.filter(match -> match.getBlueExternalTeamId() != null && !match.getBlueExternalTeamId().isBlank())
				.filter(match -> match.getRedExternalTeamId() != null && !match.getRedExternalTeamId().isBlank())
				.toList();

		return backfillGameExternalIdentitiesForMatches(targetLeagues, matches);
	}

	private GameExternalIdentityBackfillResult backfillGameExternalIdentitiesForMatches(
			List<String> targetLeagues,
			List<LeagueMatch> inputMatches) {
		List<LeagueMatch> matches = inputMatches.stream()
				.filter(match -> match.getLeagueName() != null
						&& targetLeagues.contains(match.getLeagueName().toUpperCase()))
				.filter(match -> match.getMatchDate() != null)
				.filter(match -> !isPlaceholderTeamName(match.getBlueTeamName())
						&& !isPlaceholderTeamName(match.getRedTeamName()))
				.filter(match -> match.getBlueExternalTeamId() != null && !match.getBlueExternalTeamId().isBlank())
				.filter(match -> match.getRedExternalTeamId() != null && !match.getRedExternalTeamId().isBlank())
				.toList();
		log.info("Game identity backfill started: leagues={}, targetMatches={}", targetLeagues,
				matches.size());

		if (matches.isEmpty()) {
			return new GameExternalIdentityBackfillResult(targetLeagues, 0, 0, 0, 0, 0, 0);
		}

		Map<String, List<LeagueMatchGame>> gameRowsByMatchId = leagueMatchGameRepository
				.findAllByLeagueMatchIdsOrderByMatchAndGameOrder(matches.stream().map(LeagueMatch::getId).toList())
				.stream()
				.collect(Collectors.groupingBy(row -> row.getLeagueMatch().getId(), LinkedHashMap::new,
						Collectors.toList()));

		Map<String, Long> internalTeamIdByExternalId = teamExternalIdentityRepository.findBySourceAndExternalTeamIdIn(
				LOLESPORTS_SOURCE,
				matches.stream()
						.flatMap(match -> java.util.stream.Stream.of(match.getBlueExternalTeamId(),
								match.getRedExternalTeamId()))
						.filter(value -> value != null && !value.isBlank())
						.collect(Collectors.toSet()))
				.stream()
				.collect(Collectors.toMap(TeamExternalIdentity::getExternalTeamId,
						identity -> identity.getTeam().getId()));

		Map<String, GameExternalIdentity> existingByExternalGameId = gameExternalIdentityRepository
				.findBySourceAndExternalGameIdIn(
						LOLESPORTS_SOURCE,
						gameRowsByMatchId.values().stream()
								.flatMap(List::stream)
								.map(LeagueMatchGame::getGameId)
								.filter(value -> value != null && !value.isBlank())
								.collect(Collectors.toSet()))
				.stream()
				.collect(Collectors.toMap(GameExternalIdentity::getExternalGameId, identity -> identity));

		Map<LocalDate, List<LeagueMatch>> matchesByDate = matches.stream()
				.collect(Collectors.groupingBy(match -> match.getMatchDate().toLocalDate(), LinkedHashMap::new,
						Collectors.toList()));

		int targetGameRows = 0;
		int created = 0;
		int updated = 0;
		int unresolved = 0;
		int conflicts = 0;
		int processed = 0;

		for (Map.Entry<LocalDate, List<LeagueMatch>> entry : matchesByDate.entrySet()) {
			List<Game> dailyGames = loadGamesByDate(entry.getKey());
			List<GameExternalIdentity> dirtyIdentities = new ArrayList<>();

			for (LeagueMatch match : entry.getValue()) {
				processed++;
				if (processed % 100 == 0) {
					log.info("Game identity backfill progress: {}/{}", processed, matches.size());
				}

				Long blueTeamId = internalTeamIdByExternalId.get(match.getBlueExternalTeamId());
				Long redTeamId = internalTeamIdByExternalId.get(match.getRedExternalTeamId());
				List<LeagueMatchGame> gameRows = gameRowsByMatchId.getOrDefault(match.getId(), List.of());

				if (gameRows.isEmpty()) {
					continue;
				}
				if (blueTeamId == null || redTeamId == null) {
					unresolved += gameRows.size();
					continue;
				}

				for (LeagueMatchGame gameRow : gameRows) {
					if (gameRow.getGameId() == null || gameRow.getGameId().isBlank()) {
						continue;
					}
					if (!shouldTrackGameRow(match, gameRow.getGameOrder())) {
						continue;
					}
					targetGameRows++;

					GameResolution resolution = resolveInternalGame(match, gameRow, blueTeamId, redTeamId, dailyGames);
					if (resolution.isConflict()) {
						conflicts++;
						continue;
					}
					if (resolution.game() == null) {
						unresolved++;
						continue;
					}

					GameExternalIdentity existingIdentity = existingByExternalGameId.get(gameRow.getGameId());
					if (existingIdentity != null) {
						if (!existingIdentity.getGame().getId().equals(resolution.game().getId())) {
							conflicts++;
							log.warn(
									"Game identity conflict: externalGameId={} existingGameId={} resolvedGameId={} matchId={} order={}",
									gameRow.getGameId(),
									existingIdentity.getGame().getId(),
									resolution.game().getId(),
									match.getId(),
									gameRow.getGameOrder());
							continue;
						}

						if (hasGameIdentityMetadataChange(existingIdentity, match, gameRow, resolution)) {
							existingIdentity.updateMatchMetadata(
									match.getId(),
									match.getLeagueName(),
									match.getMatchDate().toLocalDate(),
									gameRow.getGameOrder(),
									resolution.matchedBy(),
									resolution.confidence());
							dirtyIdentities.add(existingIdentity);
							updated++;
						}
						continue;
					}

					GameExternalIdentity newIdentity = GameExternalIdentity.builder()
							.source(LOLESPORTS_SOURCE)
							.externalGameId(gameRow.getGameId())
							.game(resolution.game())
							.externalMatchId(match.getId())
							.externalLeagueName(match.getLeagueName())
							.matchDate(match.getMatchDate().toLocalDate())
							.gameOrder(gameRow.getGameOrder())
							.matchedBy(resolution.matchedBy())
							.confidence(resolution.confidence())
							.build();
					dirtyIdentities.add(newIdentity);
					existingByExternalGameId.put(gameRow.getGameId(), newIdentity);
					created++;
				}
			}

			if (!dirtyIdentities.isEmpty()) {
				transactionTemplate.executeWithoutResult(status -> gameExternalIdentityRepository.saveAll(dirtyIdentities));
			}
		}

		return new GameExternalIdentityBackfillResult(targetLeagues, matches.size(), targetGameRows, created, updated,
				unresolved, conflicts);
	}

	private AutoBackfillRecentMatchesResult autoBackfillRecentMatches(
			String leagueSlug,
			List<MatchResultDto> matches,
			Set<String> dirtyMatchIds) {
		if (matches == null || matches.isEmpty()) {
			return new AutoBackfillRecentMatchesResult(
					0,
					new TeamIdentityBackfillResult(List.of(), 0, 0, 0, 0, 0, 0),
					new MatchGameIdSyncResult(0, 0, 0, 0),
					new GameExternalIdentityBackfillResult(List.of(), 0, 0, 0, 0, 0, 0));
		}

		String normalizedLeague = leagueSlug == null ? "" : leagueSlug.trim().toUpperCase();
		TeamIdentityBackfillResult teamIdentityResult = syncTeamExternalIdentitiesFromMatches(normalizedLeague, matches);

		List<String> pageMatchIds = matches.stream()
				.map(MatchResultDto::getMatchId)
				.filter(id -> id != null && !id.isBlank())
				.toList();
		if (pageMatchIds.isEmpty()) {
			return new AutoBackfillRecentMatchesResult(
					0,
					teamIdentityResult,
					new MatchGameIdSyncResult(0, 0, 0, 0),
					new GameExternalIdentityBackfillResult(List.of(normalizedLeague), 0, 0, 0, 0, 0, 0));
		}

		List<LeagueMatch> pageMatches = leagueMatchRepository.findAllById(pageMatchIds).stream()
				.filter(match -> match.getMatchDate() != null)
				.filter(match -> !"unstarted".equalsIgnoreCase(match.getState()))
				.toList();
		if (pageMatches.isEmpty()) {
			return new AutoBackfillRecentMatchesResult(
					0,
					teamIdentityResult,
					new MatchGameIdSyncResult(0, 0, 0, 0),
					new GameExternalIdentityBackfillResult(List.of(normalizedLeague), 0, 0, 0, 0, 0, 0));
		}

		Map<String, List<LeagueMatchGame>> gameRowsByMatchId = leagueMatchGameRepository
				.findAllByLeagueMatchIdsOrderByMatchAndGameOrder(pageMatches.stream().map(LeagueMatch::getId).toList())
				.stream()
				.collect(Collectors.groupingBy(row -> row.getLeagueMatch().getId(), LinkedHashMap::new,
						Collectors.toList()));

		List<LeagueMatch> gameIdCandidates = pageMatches.stream()
				.filter(match -> dirtyMatchIds.contains(match.getId())
						|| gameRowsByMatchId.getOrDefault(match.getId(), List.of()).isEmpty())
				.toList();
		MatchGameIdSyncResult gameIdSyncResult = syncLeagueMatchGameIdsForMatches(gameIdCandidates);

		List<LeagueMatchGameRepository.MappedGameRow> mappedRows = leagueMatchGameRepository
				.findMappedGameRowsByMatchIds(pageMatches.stream().map(LeagueMatch::getId).toList(), LOLESPORTS_SOURCE);
		Map<String, List<LeagueMatchGameRepository.MappedGameRow>> mappedRowsByMatchId = mappedRows.stream()
				.collect(Collectors.groupingBy(LeagueMatchGameRepository.MappedGameRow::getMatchId, LinkedHashMap::new,
						Collectors.toList()));

		List<LeagueMatch> identityCandidates = pageMatches.stream()
				.filter(match -> dirtyMatchIds.contains(match.getId()) || hasUnmappedTrackedRows(match,
						mappedRowsByMatchId.getOrDefault(match.getId(), List.of())))
				.toList();
		GameExternalIdentityBackfillResult gameIdentityResult = backfillGameExternalIdentitiesForMatches(
				List.of(normalizedLeague),
				identityCandidates);

		log.info(
				"Auto backfill completed for league={}: teamIdentities(created={}, updated={}, unresolved={}), gameIds(target={}, updated={}, unchanged={}, failed={}), gameIdentities(targetMatches={}, targetGameRows={}, created={}, updated={}, unresolved={}, conflicts={})",
				normalizedLeague,
				teamIdentityResult.createdMappings(),
				teamIdentityResult.updatedMappings(),
				teamIdentityResult.unresolvedMappings(),
				gameIdSyncResult.targetMatches(),
				gameIdSyncResult.updatedMatches(),
				gameIdSyncResult.unchangedMatches(),
				gameIdSyncResult.failedMatches(),
				gameIdentityResult.targetMatches(),
				gameIdentityResult.targetGameRows(),
				gameIdentityResult.createdMappings(),
				gameIdentityResult.updatedMappings(),
				gameIdentityResult.unresolvedMappings(),
				gameIdentityResult.conflicts());
		return new AutoBackfillRecentMatchesResult(
				pageMatches.size(),
				teamIdentityResult,
				gameIdSyncResult,
				gameIdentityResult);
	}

	private MatchSyncUpsertResult upsertLeagueMatches(String leagueSlug, List<MatchResultDto> matches) {
		Map<String, LeagueMatch> existingMatchesById = leagueMatchRepository
				.findAllById(matches.stream().map(MatchResultDto::getMatchId).toList())
				.stream()
				.collect(Collectors.toMap(LeagueMatch::getId, match -> match));

		List<LeagueMatch> dirtyMatches = new ArrayList<>();
		Set<String> dirtyMatchIds = new java.util.LinkedHashSet<>();
		int inserted = 0;
		int updated = 0;
		int skipped = 0;

		for (MatchResultDto dto : matches) {
			try {
				LeagueMatch incoming = convertToEntity(dto, leagueSlug);
				applySeasonIfResolvable(incoming);
				LeagueMatch existing = existingMatchesById.get(dto.getMatchId());

				if (existing == null) {
					// 첫 관측 시점의 스코어로 시작 — 완봉 진행분은 즉시 귀속, 혼합 스코어는 '?' 로 자리만 잡는다.
					incoming.applySetWinners(advanceSetWinners(
							null, incoming.getBlueScore(), incoming.getRedScore(), isCompleted(incoming)));
					dirtyMatches.add(incoming);
					dirtyMatchIds.add(incoming.getId());
					inserted++;
					continue;
				}

				if (isResultRegression(existing, incoming)) {
					log.warn("업스트림이 완료된 매치를 결과 없는 상태로 되돌려 기존 결과를 유지한다. "
									+ "matchId={} 유지={}:{}({}) 수신={}:{}({})",
							existing.getId(), existing.getBlueScore(), existing.getRedScore(), existing.getState(),
							incoming.getBlueScore(), incoming.getRedScore(), incoming.getState());
					incoming.restoreResult(existing.getState(), existing.getBlueScore(), existing.getRedScore());
				}

				if (!hasRealtimeRelevantChange(existing, incoming)) {
					// 시즌·bestOf 만 비어 있으면 채운다 (dirtyMatchIds에는 넣지 않아 게임 ID 재동기화는 트리거하지 않음)
					boolean filled = false;
					if (existing.getSeasonYear() == null) {
						applySeasonIfResolvable(existing);
						filled = existing.getSeasonYear() != null;
					}
					if (existing.getBestOf() == null && incoming.getBestOf() != null) {
						existing.applyBestOf(incoming.getBestOf());
						filled = true;
					}
					if (filled) {
						dirtyMatches.add(existing);
					}
					skipped++;
					continue;
				}

				existing.update(
						incoming.getLeagueName(),
						incoming.getMatchTitle(),
						incoming.getMatchDate(),
						incoming.getState(),
						incoming.getBlueTeamCode(),
						incoming.getBlueTeamName(),
						incoming.getBlueExternalTeamId(),
						incoming.getBlueTeamImageUrl(),
						incoming.getBlueScore(),
						incoming.getRedTeamCode(),
						incoming.getRedTeamName(),
						incoming.getRedExternalTeamId(),
						incoming.getRedTeamImageUrl(),
						incoming.getRedScore(),
						incoming.isHasVod(),
						incoming.getMatchDetailsJson(),
						incoming.getLastUpdated());
				existing.applySetWinners(advanceSetWinners(
						existing.getSetWinners(), incoming.getBlueScore(), incoming.getRedScore(),
						isCompleted(incoming)));
				existing.applyBestOf(incoming.getBestOf());
				applySeasonIfResolvable(existing);
				dirtyMatches.add(existing);
				dirtyMatchIds.add(existing.getId());
				updated++;
			} catch (Exception e) {
				log.error("Failed to save match: {}", dto.getMatchId(), e);
			}
		}

		if (!dirtyMatches.isEmpty()) {
			leagueMatchRepository.saveAll(dirtyMatches);
		}

		return new MatchSyncUpsertResult(dirtyMatchIds, inserted, updated, skipped);
	}

	private void applySeasonIfResolvable(LeagueMatch match) {
		if (match.getMatchDate() == null) {
			return;
		}
		leagueSeasonResolver.resolve(match.getLeagueName(), match.getMatchDate())
				.ifPresent(season -> match.applySeason(season.year(), season.split()));
	}

	private MatchGameIdSyncResult syncLeagueMatchGameIdsForMatches(List<LeagueMatch> matches) {
		if (matches == null || matches.isEmpty()) {
			return new MatchGameIdSyncResult(0, 0, 0, 0);
		}

		int updated = 0;
		int unchanged = 0;
		int failed = 0;

		for (LeagueMatch match : matches) {
			try {
				List<String> gameIds = worldsService.getGameIdsByMatchId(match.getId()).stream()
						.filter(gameId -> gameId != null && !gameId.isBlank())
						.toList();
				List<String> currentGameIds = leagueMatchGameRepository
						.findByLeagueMatch_IdOrderByGameOrderAsc(match.getId())
						.stream()
						.map(LeagueMatchGame::getGameId)
						.toList();

				if (currentGameIds.equals(gameIds)) {
					unchanged++;
					continue;
				}

				// 자동 경로에서는 일시적인 외부 API 공백으로 기존 매핑을 지우지 않도록 보호한다.
				if (gameIds.isEmpty() && !currentGameIds.isEmpty()) {
					log.warn("Skipping empty auto game-id sync for matchId={} state={}", match.getId(), match.getState());
					failed++;
					continue;
				}

				syncLeagueMatchGames(match.getId(), gameIds);
				updated++;
				Thread.sleep(120);
			} catch (Exception e) {
				failed++;
				log.warn("Failed to auto sync gameIds for matchId={}: {}", match.getId(), e.getMessage());
			}
		}

		return new MatchGameIdSyncResult(matches.size(), updated, unchanged, failed);
	}

	private boolean hasUnmappedTrackedRows(
			LeagueMatch match,
			List<LeagueMatchGameRepository.MappedGameRow> mappedRows) {
		List<LeagueMatchGameRepository.MappedGameRow> trackedRows = mappedRows.stream()
				.filter(row -> shouldTrackGameRow(match, row.getGameOrder()))
				.toList();
		if (trackedRows.isEmpty()) {
			return false;
		}
		return trackedRows.stream().anyMatch(row -> row.getInternalGameId() == null);
	}

	// [Admin용] 모든 대상 리그의 전체 과거 데이터 동기화
	public int syncAllLeaguesFullHistory() {
		log.info("Starting FULL history sync for ALL target leagues: {}", LeagueConstants.TARGET_LEAGUES);
		int totalSynced = 0;
		for (String league : LeagueConstants.TARGET_LEAGUES) {
			try {
				totalSynced += syncFullHistory(league);
				// 리그 사이에는 넉넉하게 10초 대기 (API 차단 방지)
				Thread.sleep(10000);
			} catch (Exception e) {
				log.error("Failed to sync all history for league: {}", league, e);
			}
		}
		log.info("Completed FULL history sync for ALL leagues. Grand total: {}", totalSynced);
		return totalSynced;
	}

	// [Admin용] 특정 리그의 전체 과거 데이터 동기화
	public int syncFullHistory(String leagueSlug) {
		log.info("Starting FULL history sync for league: {}", leagueSlug);
		String pageToken = null;
		int totalSynced = 0;
		int pageCount = 0;

		while (true) {
			try {
				pageCount++;
				log.info("Fetching page {} for league: {} (token: {})", pageCount, leagueSlug, pageToken);

				MatchResponseWrapper response = worldsService.getWorldsMatches(pageToken, leagueSlug);
				List<MatchResultDto> matches = response.getMatches();

				if (matches == null || matches.isEmpty()) {
					log.info("No more matches found for league: {} at page {}", leagueSlug, pageCount);
					break;
				}

				for (MatchResultDto dto : matches) {
					try {
						LeagueMatch entity = convertToEntity(dto, leagueSlug);
						leagueMatchRepository.save(entity);
						totalSynced++;
					} catch (Exception e) {
						log.error("Failed to save match: {}", dto.getMatchId(), e);
					}
				}

				// Team Metadata Sync per page
				updateTeamMetadataFromMatches(matches);

				pageToken = response.getNextPageToken();
				if (pageToken == null || pageToken.isEmpty()) {
					log.info("End of pages reached for league: {}", leagueSlug);
					break;
				}

				// API 부하 방지
				Thread.sleep(2000);

			} catch (Exception e) {
				log.error("Error during history sync for league: {} at page {}", leagueSlug, pageCount, e);
				break;
			}
		}

		log.info("Completed FULL history sync for league: {}. Total synced: {}", leagueSlug, totalSynced);
		return totalSynced;
	}

	// [API용] DB에서 특정 리그의 경기 목록 조회 (날짜 필터 추가)
	@Transactional(readOnly = true)
	public MatchResponseWrapper getMatchesFromDb(String leagueSlug, String date) {
		List<LeagueMatch> entities;
		boolean isAllLeagues = leagueSlug == null || leagueSlug.isEmpty() || "ALL".equalsIgnoreCase(leagueSlug);

		if (date != null && !date.isEmpty()) {
			try {
				LocalDateTime start;
				LocalDateTime end;

				if (date.length() == 10) { // YYYY-MM-DD
					java.time.LocalDate localDate = java.time.LocalDate.parse(date);
					start = localDate.atStartOfDay();
					end = localDate.atTime(23, 59, 59);
				} else if (date.length() == 7) { // YYYY-MM
					java.time.YearMonth yearMonth = java.time.YearMonth.parse(date);
					start = yearMonth.atDay(1).atStartOfDay();
					end = yearMonth.atEndOfMonth().atTime(23, 59, 59);
				} else {
					throw new IllegalArgumentException("Invalid date format. Use YYYY-MM-DD or YYYY-MM");
				}

				log.info("Searching matches for league: {} between {} and {}", isAllLeagues ? "ALL" : leagueSlug, start,
						end);

				if (isAllLeagues) {
					entities = leagueMatchRepository.findByDateRange(start, end);
				} else {
					entities = leagueMatchRepository.findByLeagueNameAndDateRange(leagueSlug, start, end);
				}
			} catch (Exception e) {
				log.error("Date parsing failed for input: {}", date);
				return MatchResponseWrapper.builder().matches(List.of()).build();
			}
		} else {
			// 날짜가 없을 때
			if (isAllLeagues) {
				// 전체 리그 최신순 50개 (findAll + sort)
				entities = leagueMatchRepository
						.findAll(org.springframework.data.domain.PageRequest.of(0, 50,
								org.springframework.data.domain.Sort
										.by(org.springframework.data.domain.Sort.Direction.DESC, "matchDate")))
						.getContent();
			} else {
				// 특정 리그 최신순 50개
				entities = leagueMatchRepository.findByLeagueNameOrderByMatchDateDesc(leagueSlug,
						org.springframework.data.domain.PageRequest.of(0, 50));
			}
		}

		List<MatchResultDto> dtos = entities.stream().map(this::convertToDto).collect(Collectors.toList());

		return MatchResponseWrapper.builder().matches(dtos).nextPageToken(null).build();
	}

	public MatchResponseWrapper getMatchesFromDb(String leagueSlug, LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
			return MatchResponseWrapper.builder().matches(List.of()).build();
		}

		boolean isAllLeagues = leagueSlug == null || leagueSlug.isEmpty() || "ALL".equalsIgnoreCase(leagueSlug);
		LocalDateTime start = startDate.atStartOfDay();
		LocalDateTime end = endDate.atTime(23, 59, 59);

		log.info("Searching matches for league: {} between {} and {}", isAllLeagues ? "ALL" : leagueSlug, start, end);

		List<LeagueMatch> entities = isAllLeagues
				? leagueMatchRepository.findByDateRange(start, end)
				: leagueMatchRepository.findByLeagueNameAndDateRange(leagueSlug, start, end);

		List<MatchResultDto> dtos = entities.stream().map(this::convertToDto).collect(Collectors.toList());
		return MatchResponseWrapper.builder().matches(dtos).nextPageToken(null).build();
	}

	@Transactional(readOnly = true)
	public Optional<MatchResultDto> getMatchFromDbById(String matchId) {
		return leagueMatchRepository.findById(matchId).map(this::convertToDto);
	}

	@Transactional(readOnly = true)
	public List<MatchResultDto> getRecentMatchesFromDb(String leagueSlug) {
		List<LeagueMatch> entities = leagueMatchRepository.findTop3ByLeagueNameOrderByMatchDateDesc(leagueSlug);
		if (entities.isEmpty()) {
			// DB에 없으면(초기 긁어오기 시도
			syncMatches(leagueSlug);
			entities = leagueMatchRepository.findTop3ByLeagueNameOrderByMatchDateDesc(leagueSlug);
		}
		return entities.stream().map(this::convertToDto).collect(Collectors.toList());
	}

	private LeagueMatch convertToEntity(MatchResultDto dto, String leagueSlug) throws JsonProcessingException {
		// "2026-01-05T17:00:00Z" -> LocalDateTime 파싱
		// 라이엇 API 날짜 포맷은 ISO-8601 (ex: 2024-10-19T12:00:00Z)
		LocalDateTime matchDate = LocalDateTime.parse(dto.getMatchDate(), DateTimeFormatter.ISO_DATE_TIME);

		String jsonDetails = objectMapper.writeValueAsString(dto.getSets());
		boolean hasVod = dto.getSets() != null && !dto.getSets().isEmpty()
				&& dto.getSets().stream().anyMatch(s -> s.getVodUrl() != null && !s.getVodUrl().isEmpty());

		String resolvedLeagueName = resolveLeagueName(dto.getLeagueName(), leagueSlug);

		return LeagueMatch.builder().id(dto.getMatchId()).leagueName(resolvedLeagueName).matchTitle(dto.getMatchTitle())
				.matchDate(matchDate).state(dto.getState()) // [수정]
															// DTO에서
															// 상태
															// 가져오기
				.blueTeamCode(dto.getBlueTeam().getCode()).blueTeamName(dto.getBlueTeam().getName())
				.blueExternalTeamId(dto.getBlueTeam().getExternalTeamId())
				.blueTeamImageUrl(dto.getBlueTeam().getImageUrl()).blueScore(dto.getBlueTeam().getWins())
				.redTeamCode(dto.getRedTeam().getCode()).redTeamName(dto.getRedTeam().getName())
				.redExternalTeamId(dto.getRedTeam().getExternalTeamId())
				.redTeamImageUrl(dto.getRedTeam().getImageUrl()).redScore(dto.getRedTeam().getWins()).hasVod(hasVod)
				.bestOf(dto.getBestOf())
				.matchDetailsJson(jsonDetails).lastUpdated(LocalDateTime.now()).build();
	}

	private MatchResultDto convertToDto(LeagueMatch entity) {
		List<MatchResultDto.SetVod> sets = new ArrayList<>();
		try {
			if (entity.getMatchDetailsJson() != null) {
				sets = objectMapper.readValue(entity.getMatchDetailsJson(), new TypeReference<>() {
				});
			}
		} catch (JsonProcessingException e) {
			log.error("JSON parsing failed for match: {}", entity.getId(), e);
		}

		String liveStreamUrl = null;
		if ("inProgress".equalsIgnoreCase(entity.getState())) {
			liveStreamUrl = LeagueConstants.getLiveStreamUrl(entity.getLeagueName());
		}
		List<String> gameIds = leagueMatchGameRepository.findByLeagueMatch_IdOrderByGameOrderAsc(entity.getId()).stream()
				.map(LeagueMatchGame::getGameId)
				.filter(gameId -> gameId != null && !gameId.isBlank())
				.distinct()
				.toList();
		List<String> liveGameIds = resolveLiveGameIds(entity, gameIds);

		return MatchResultDto.builder().matchId(entity.getId()).leagueName(entity.getLeagueName())
				.matchTitle(entity.getMatchTitle()).matchDate(entity.getMatchDate().toString()) // ISO
																								// format
																								// string
				.state(entity.getState()) // [수정] Entity 상태 DTO로 전달
				.score(entity.getBlueScore() + " : " + entity.getRedScore())
				.bestOf(entity.getBestOf())
				.blueTeam(
						MatchResultDto.TeamInfo.builder()
								.externalTeamId(entity.getBlueExternalTeamId())
								.code(entity.getBlueTeamCode()).name(entity.getBlueTeamName())
								.imageUrl(entity.getBlueTeamImageUrl()).wins(entity.getBlueScore()).build())
				.redTeam(MatchResultDto.TeamInfo.builder()
						.externalTeamId(entity.getRedExternalTeamId())
						.code(entity.getRedTeamCode()).name(entity.getRedTeamName())
						.imageUrl(entity.getRedTeamImageUrl()).wins(entity.getRedScore()).build())
				.sets(sets)
				.gameIds(gameIds)
				.liveGameIds(liveGameIds)
				.liveStreamUrl(liveStreamUrl).build();
	}

	private List<String> resolveLiveGameIds(LeagueMatch entity, List<String> gameIds) {
		if (!"inProgress".equalsIgnoreCase(entity.getState()) || gameIds == null || gameIds.isEmpty()) {
			return List.of();
		}
		int completedGames = entity.getBlueScore() + entity.getRedScore();
		int currentGameIndex = Math.min(completedGames, gameIds.size() - 1);
		if (currentGameIndex < 0 || currentGameIndex >= gameIds.size()) {
			return List.of();
		}
		return List.of(gameIds.get(currentGameIndex));
	}

	private boolean hasRealtimeRelevantChange(LeagueMatch existing, LeagueMatch incoming) {
		if (!java.util.Objects.equals(existing.getLeagueName(), incoming.getLeagueName())) {
			return true;
		}
		if (!java.util.Objects.equals(existing.getMatchDate(), incoming.getMatchDate())) {
			return true;
		}
		if (!java.util.Objects.equals(existing.getMatchTitle(), incoming.getMatchTitle())) {
			return true;
		}
		if (!java.util.Objects.equals(existing.getState(), incoming.getState())) {
			return true;
		}
		if (!java.util.Objects.equals(existing.getBlueScore(), incoming.getBlueScore())) {
			return true;
		}
		if (!java.util.Objects.equals(existing.getRedScore(), incoming.getRedScore())) {
			return true;
		}
		if (existing.isHasVod() != incoming.isHasVod()) {
			return true;
		}
		if (!java.util.Objects.equals(existing.getMatchDetailsJson(), incoming.getMatchDetailsJson())) {
			return true;
		}
		if (!java.util.Objects.equals(existing.getBlueExternalTeamId(), incoming.getBlueExternalTeamId())) {
			return true;
		}
		return !java.util.Objects.equals(existing.getRedExternalTeamId(), incoming.getRedExternalTeamId());
	}

	private String resolveLeagueName(String detailLeagueName, String requestedLeagueSlug) {
		if (detailLeagueName != null && !detailLeagueName.isBlank()) {
			return detailLeagueName.trim().toUpperCase();
		}
		return requestedLeagueSlug == null ? "" : requestedLeagueSlug.trim().toUpperCase();
	}

	private Map<String, List<String>> loadGameIdsByMatchIds(List<LeagueMatch> matches) {
		if (matches == null || matches.isEmpty()) {
			return Map.of();
		}

		List<String> matchIds = matches.stream()
				.map(LeagueMatch::getId)
				.filter(id -> id != null && !id.isBlank())
				.toList();
		if (matchIds.isEmpty()) {
			return Map.of();
		}

		Map<String, List<String>> result = new HashMap<>();
		List<LeagueMatchGame> rows = leagueMatchGameRepository
				.findAllByLeagueMatchIdsOrderByMatchAndGameOrder(matchIds);
		for (LeagueMatchGame row : rows) {
			String matchId = row.getLeagueMatch().getId();
			result.computeIfAbsent(matchId, ignored -> new ArrayList<>()).add(row.getGameId());
		}
		return result;
	}

	protected void syncLeagueMatchGames(String matchId, List<String> gameIds) {
		if (matchId == null || matchId.isBlank()) {
			return;
		}

		transactionTemplate.executeWithoutResult(status -> {
			leagueMatchGameRepository.deleteAllByMatchId(matchId);
			if (gameIds == null || gameIds.isEmpty()) {
				return;
			}

			LeagueMatch matchRef = leagueMatchRepository.getReferenceById(matchId);
			List<LeagueMatchGame> rows = new ArrayList<>();
			int order = 1;
			for (String gameId : gameIds) {
				if (gameId == null || gameId.isBlank()) {
					continue;
				}
				rows.add(new LeagueMatchGame(matchRef, gameId, order++));
			}
			if (!rows.isEmpty()) {
				leagueMatchGameRepository.saveAll(rows);
			}
		});
	}

	public GameIdBackfillResult backfillGameIdsForYear(int year, int limit) {
		LocalDateTime start = java.time.LocalDate.of(year, 1, 1).atStartOfDay();
		LocalDateTime end = java.time.LocalDate.of(year + 1, 1, 1).atStartOfDay().minusNanos(1);

		List<LeagueMatch> candidates = leagueMatchRepository.findByDateRange(start, end);
		if (limit > 0 && candidates.size() > limit) {
			candidates = candidates.subList(0, limit);
		}

		int updated = 0;
		int unchanged = 0;
		int failed = 0;

		for (LeagueMatch match : candidates) {
			try {
				List<String> gameIds = worldsService.getGameIdsByMatchId(match.getId()).stream()
						.filter(gameId -> gameId != null && !gameId.isBlank())
						.toList();
				List<String> currentGameIds = leagueMatchGameRepository
						.findByLeagueMatch_IdOrderByGameOrderAsc(match.getId())
						.stream()
						.map(LeagueMatchGame::getGameId)
						.toList();

				if (currentGameIds.equals(gameIds)) {
					unchanged++;
				} else {
					syncLeagueMatchGames(match.getId(), gameIds);
					updated++;
				}

				// API 부하 완화
				Thread.sleep(120);
			} catch (Exception e) {
				failed++;
				log.warn("Failed to backfill gameIds for matchId={}: {}", match.getId(), e.getMessage());
			}
		}

		return new GameIdBackfillResult(year, candidates.size(), updated, unchanged, failed);
	}

	public record GameIdBackfillResult(
			int year,
			int targetMatches,
			int updatedMatches,
			int unchangedMatches,
			int failedMatches) {
	}

	// @Transactional // Removed to avoid holding all entities in memory for the
	// whole duration
	protected int updateTeamMetadataFromMatches(List<MatchResultDto> matches) {
		if (matches == null || matches.isEmpty())
			return 0;

		log.info("Starting context-aware team metadata sync for {} matches...", matches.size());

		Map<String, Team> teamsByExternalId = loadTeamsByExternalIds(matches);
		List<MatchResultDto> fallbackMatches = new ArrayList<>();
		int totalUpdated = 0;
		Map<Long, Team> dirtyTeams = new LinkedHashMap<>();

		for (MatchResultDto match : matches) {
			DirectTeamMetadataSyncResult directResult = updateTeamMetadataByExternalIdentity(match, teamsByExternalId,
					dirtyTeams);
			totalUpdated += directResult.updatedCount();
			if (directResult.requiresFallback()) {
				fallbackMatches.add(match);
			}
		}

		if (!fallbackMatches.isEmpty()) {
			log.info("Falling back to name/date matching for {} matches without external team mapping.",
					fallbackMatches.size());
		}

		// Group unresolved matches by Date (yyyy-MM-dd) to avoid fetching huge range if
		// dates are scattered
		Map<LocalDate, List<MatchResultDto>> matchesByDate = fallbackMatches.stream()
				.filter(m -> m.getMatchDate() != null)
				.collect(Collectors.groupingBy(m -> {
					try {
						return LocalDateTime.parse(m.getMatchDate(), DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
					} catch (Exception e) {
						return LocalDate.MIN; // Should filter invalid before, but safe fallback
					}
				}));

		for (Map.Entry<LocalDate, List<MatchResultDto>> entry : matchesByDate.entrySet()) {
			LocalDate date = entry.getKey();
			List<MatchResultDto> dailyMatches = entry.getValue();

			if (date.equals(LocalDate.MIN))
				continue;

			// Fetch games for this specific date (+/- 1 day buffer)
			LocalDateTime searchStart = date.atStartOfDay().minusHours(24);
			LocalDateTime searchEnd = date.atTime(23, 59, 59).plusHours(24);

			log.info("Processing {} matches for date: {} (Window: {} - {})", dailyMatches.size(), date, searchStart,
					searchEnd);

			List<Game> candidateGames = gameRepository
					.findAllWithParticipantsByActualGameStartTimeBetween(searchStart, searchEnd);

			log.debug("Found {} candidate games in DB for date {}.", candidateGames.size(), date);

			for (MatchResultDto match : dailyMatches) {
				totalUpdated += processSingleMatchSync(match, candidateGames, dirtyTeams);
			}
		}

		if (!dirtyTeams.isEmpty()) {
			teamRepository.saveAll(dirtyTeams.values());
		}

		log.info("Team metadata sync completed. Updated {} records, {} unique teams (batch save).",
				totalUpdated, dirtyTeams.size());
		return totalUpdated;
	}

	private int processSingleMatchSync(
			MatchResultDto match,
			List<Game> candidateGames,
			Map<Long, Team> dirtyTeams) {
		try {
			LocalDateTime matchDate = LocalDateTime.parse(match.getMatchDate(), DateTimeFormatter.ISO_DATE_TIME);
			String normBlue = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(match.getBlueTeam().getName());
			String normRed = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(match.getRedTeam().getName());

			// Find matching Game
			Game matchedGame = candidateGames.stream().filter(game -> {
				// Check Date (redundant if window is tight, but safe)
				if (!game.getActualGameStartTime().toLocalDate().equals(matchDate.toLocalDate())) {
					return false;
				}

				// Check Teams
				String gBlue = getTeamNameFromGame(game, "Blue");
				String gRed = getTeamNameFromGame(game, "Red");
				String normGBlue = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(gBlue);
				String normGRed = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(gRed);

				// Match Logic
				return (normGBlue.equalsIgnoreCase(normBlue) && normGRed.equalsIgnoreCase(normRed))
						|| (normGBlue.equalsIgnoreCase(normRed) && normGRed.equalsIgnoreCase(normBlue));
			}).findFirst().orElse(null);

			if (matchedGame != null) {
				int count = 0;
				count += updateTeamIfMatched(matchedGame, match.getBlueTeam(), dirtyTeams);
				count += updateTeamIfMatched(matchedGame, match.getRedTeam(), dirtyTeams);
				return count;
			}
		} catch (Exception e) {
			log.warn("Error processing match metadata sync for matchId: {}", match.getMatchId(), e);
		}
		return 0;
	}

	private int updateTeamIfMatched(
			Game game,
			MatchResultDto.TeamInfo info,
			Map<Long, Team> dirtyTeams) {
		if (info == null || info.getName() == null)
			return 0;
		if ((info.getName() == null || info.getName().isBlank())
				&& (info.getCode() == null || info.getCode().isEmpty())
				&& (info.getImageUrl() == null || info.getImageUrl().isEmpty())) {
			return 0;
		}

		String normInfoName = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(info.getName());

		// Find the participant team entity + strict match check
		return game.getParticipants().stream().map(p -> p.getTeam()).filter(team -> {
			String normTeamName = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(team.getName());
			return normTeamName.equalsIgnoreCase(normInfoName);
		}).findFirst().map(team -> {
			TeamMetadataUpdate update = prepareTeamMetadataUpdate(team, info);
			if (update.updated()) {
				log.info("Syncing metadata for matched team '{}' (Game ID: {})", team.getName(), game.getId());
				team.updateMetadata(update.name(), update.code(), update.imageUrl());
				dirtyTeams.put(team.getId(), team);
				return 1;
			}
			return 0;
		}).orElse(0);
	}

	private Map<String, Team> loadTeamsByExternalIds(List<MatchResultDto> matches) {
		Set<String> externalTeamIds = matches.stream()
				.flatMap(match -> java.util.stream.Stream.of(match.getBlueTeam(), match.getRedTeam()))
				.filter(info -> info != null && info.getExternalTeamId() != null && !info.getExternalTeamId().isBlank())
				.map(MatchResultDto.TeamInfo::getExternalTeamId)
				.collect(Collectors.toSet());

		if (externalTeamIds.isEmpty()) {
			return Map.of();
		}

		return teamExternalIdentityRepository.findBySourceAndExternalTeamIdIn(LOLESPORTS_SOURCE, externalTeamIds)
				.stream()
				.collect(Collectors.toMap(TeamExternalIdentity::getExternalTeamId, TeamExternalIdentity::getTeam));
	}

	private DirectTeamMetadataSyncResult updateTeamMetadataByExternalIdentity(
			MatchResultDto match,
			Map<String, Team> teamsByExternalId,
			Map<Long, Team> dirtyTeams) {
		int updatedCount = 0;
		boolean requiresFallback = false;

		for (MatchResultDto.TeamInfo teamInfo : Arrays.asList(match.getBlueTeam(), match.getRedTeam())) {
			if (!hasMetadataToSync(teamInfo)) {
				continue;
			}

			String externalTeamId = teamInfo.getExternalTeamId();
			if (externalTeamId == null || externalTeamId.isBlank()) {
				requiresFallback = true;
				continue;
			}

			Team team = teamsByExternalId.get(externalTeamId);
			if (team == null) {
				requiresFallback = true;
				continue;
			}

			updatedCount += updateTeamMetadata(team, teamInfo, dirtyTeams);
		}

		return new DirectTeamMetadataSyncResult(updatedCount, requiresFallback);
	}

	private int updateTeamMetadata(Team team, MatchResultDto.TeamInfo info, Map<Long, Team> dirtyTeams) {
		TeamMetadataUpdate update = prepareTeamMetadataUpdate(team, info);
		if (!update.updated()) {
			return 0;
		}

		log.info("Syncing metadata for team '{}' via external team mapping", team.getName());
		team.updateMetadata(update.name(), update.code(), update.imageUrl());
		dirtyTeams.put(team.getId(), team);
		return 1;
	}

	private boolean hasMetadataToSync(MatchResultDto.TeamInfo info) {
		if (info == null) {
			return false;
		}
		if (info.getName() != null && !info.getName().isEmpty()) {
			return true;
		}
		return (info.getCode() != null && !info.getCode().isEmpty())
				|| (info.getImageUrl() != null && !info.getImageUrl().isEmpty());
	}

	private TeamMetadataUpdate prepareTeamMetadataUpdate(Team team, MatchResultDto.TeamInfo info) {
		String normalizedName = normalizeIncomingTeamName(info.getName());
		String newName = team.getName();
		String newCode = team.getCode();
		String newImage = team.getImageUrl();
		boolean updated = false;

		if (normalizedName != null
				&& !normalizedName.equals(team.getName())
				&& isTeamNameAvailable(team.getId(), normalizedName)) {
			newName = normalizedName;
			updated = true;
		}

		if (info.getCode() != null && !info.getCode().isEmpty() && !info.getCode().equals(team.getCode())) {
			newCode = info.getCode();
			updated = true;
		}

		if (info.getImageUrl() != null && !info.getImageUrl().isEmpty() && !info.getImageUrl().equals(team.getImageUrl())) {
			newImage = info.getImageUrl();
			updated = true;
		}

		return new TeamMetadataUpdate(newName, newCode, newImage, updated);
	}

	private String normalizeIncomingTeamName(String teamName) {
		if (teamName == null || teamName.isBlank()) {
			return null;
		}
		return NameNormalizer.normalizeTeamName(teamName);
	}

	private boolean isTeamNameAvailable(Long teamId, String targetName) {
		return teamRepository.findByNameIgnoreCase(targetName)
				.map(existing -> {
					boolean available = existing.getId().equals(teamId);
					if (!available) {
						log.warn("Skipping team name sync for teamId={} because '{}' is already used by teamId={}",
								teamId, targetName, existing.getId());
					}
					return available;
				})
				.orElse(true);
	}

	private String getTeamNameFromGame(Game game, String side) {
		return game.getParticipants().stream().filter(p -> p.getSide().equalsIgnoreCase(side)).findFirst()
				.map(p -> p.getTeam().getName()).orElse("");
	}

	private void collectExternalTeamCandidate(
			Map<String, ExternalTeamCandidate> candidatesByExternalId,
			String league,
			MatchResultDto.TeamInfo teamInfo) {
		if (teamInfo == null || teamInfo.getExternalTeamId() == null || teamInfo.getExternalTeamId().isBlank()) {
			return;
		}
		candidatesByExternalId.putIfAbsent(
				teamInfo.getExternalTeamId(),
				new ExternalTeamCandidate(
						teamInfo.getExternalTeamId(),
						teamInfo.getName(),
						teamInfo.getCode(),
						teamInfo.getImageUrl(),
						league));
	}

	private Map<String, Team> buildExactTeamNameMap(List<Team> teams) {
		return teams.stream()
				.collect(Collectors.toMap(Team::getName, team -> team, (left, right) -> left, LinkedHashMap::new));
	}

	private Map<String, Team> buildNormalizedTeamNameMap(List<Team> teams) {
		Map<String, Team> normalizedTeams = new HashMap<>();
		Set<String> ambiguousKeys = new java.util.HashSet<>();

		for (Team team : teams) {
			String normalizedName = NameNormalizer.normalizeTeamName(team.getName());
			Team existing = normalizedTeams.putIfAbsent(normalizedName, team);
			if (existing != null && !existing.getId().equals(team.getId())) {
				ambiguousKeys.add(normalizedName);
			}
		}

		ambiguousKeys.forEach(normalizedTeams::remove);
		return normalizedTeams;
	}

	private Map<String, Team> buildTeamCodeMap(List<Team> teams) {
		Map<String, Team> teamsByCode = new HashMap<>();
		Set<String> ambiguousCodes = new java.util.HashSet<>();

		for (Team team : teams) {
			if (team.getCode() == null || team.getCode().isBlank()) {
				continue;
			}
			String normalizedCode = team.getCode().trim().toUpperCase();
			Team existing = teamsByCode.putIfAbsent(normalizedCode, team);
			if (existing != null && !existing.getId().equals(team.getId())) {
				ambiguousCodes.add(normalizedCode);
			}
		}

		ambiguousCodes.forEach(teamsByCode::remove);
		return teamsByCode;
	}

	private Team resolveTeamCandidate(
			ExternalTeamCandidate candidate,
			Map<String, Team> teamsByExactName,
			Map<String, Team> normalizedTeamsByName,
			Map<String, Team> teamsByCode) {
		String aliasTargetName = TEAM_ALIAS_TARGETS_BY_EXTERNAL_ID.get(candidate.externalTeamId());
		if (aliasTargetName != null) {
			Team aliasedTeam = teamsByExactName.get(aliasTargetName);
			if (aliasedTeam != null) {
				return aliasedTeam;
			}
		}

		if (candidate.externalName() != null && !candidate.externalName().isBlank()) {
			Team matchedByName = normalizedTeamsByName.get(NameNormalizer.normalizeTeamName(candidate.externalName()));
			if (matchedByName != null) {
				return matchedByName;
			}
		}

		if (candidate.externalCode() != null && !candidate.externalCode().isBlank()) {
			return teamsByCode.get(candidate.externalCode().trim().toUpperCase());
		}

		return null;
	}

	private String determineMatchedBy(ExternalTeamCandidate candidate, Team team) {
		String aliasTargetName = TEAM_ALIAS_TARGETS_BY_EXTERNAL_ID.get(candidate.externalTeamId());
		if (aliasTargetName != null && aliasTargetName.equals(team.getName())) {
			return "ALIAS_OVERRIDE";
		}
		if (candidate.externalName() != null
				&& NameNormalizer.normalizeTeamName(candidate.externalName())
						.equalsIgnoreCase(NameNormalizer.normalizeTeamName(team.getName()))) {
			return "NAME_NORMALIZED";
		}
		if (candidate.externalCode() != null
				&& team.getCode() != null
				&& candidate.externalCode().trim().equalsIgnoreCase(team.getCode().trim())) {
			return "CODE_EXACT";
		}
		return "MANUAL";
	}

	private boolean shouldAutoCreateTeam(ExternalTeamCandidate candidate) {
		return AUTO_CREATE_EXTERNAL_TEAM_IDS.contains(candidate.externalTeamId());
	}

	private Team createMissingTeam(ExternalTeamCandidate candidate) {
		Team team = Team.builder()
				.name(NameNormalizer.normalizeTeamName(candidate.externalName()))
				.code(candidate.externalCode())
				.imageUrl(candidate.externalImageUrl())
				.build();
		return teamRepository.save(team);
	}

	private record TeamMetadataUpdate(String name, String code, String imageUrl, boolean updated) {
	}

	private boolean hasIdentityMetadataChange(TeamExternalIdentity identity, ExternalTeamCandidate candidate,
			String matchedBy) {
		if (!java.util.Objects.equals(identity.getExternalNameRaw(), candidate.externalName())) {
			return true;
		}
		if (!java.util.Objects.equals(identity.getMatchedBy(), matchedBy)) {
			return true;
		}
		return !java.util.Objects.equals(identity.getConfidence(), confidenceFor(matchedBy));
	}

	private java.math.BigDecimal confidenceFor(String matchedBy) {
		if ("CODE_EXACT".equals(matchedBy)) {
			return new java.math.BigDecimal("0.9500");
		}
		if ("NAME_NORMALIZED".equals(matchedBy)) {
			return new java.math.BigDecimal("0.9000");
		}
		return new java.math.BigDecimal("0.5000");
	}

	private GameResolution resolveInternalGame(
			LeagueMatch match,
			LeagueMatchGame gameRow,
			Long blueTeamId,
			Long redTeamId,
			List<Game> dailyGames) {
		if (match.getMatchDate() == null || gameRow.getGameOrder() == null) {
			return GameResolution.unresolved();
		}

		String teamPairKey = buildTeamPairKey(blueTeamId, redTeamId);

		List<Game> candidates = dailyGames.stream()
				.filter(game -> java.util.Objects.equals(game.getGameNumber(), gameRow.getGameOrder()))
				.filter(game -> teamPairKey.equals(buildTeamPairKey(game)))
				.toList();

		if (candidates.isEmpty()) {
			return GameResolution.unresolved();
		}

		Set<String> allowedLeagues = candidateInternalLeagueNames(match.getLeagueName());
		List<Game> leagueMatched = candidates.stream()
				.filter(game -> allowedLeagues.contains(normalizeLeagueName(game.getLeague().getLeagueName())))
				.toList();

		if (leagueMatched.size() == 1) {
			return new GameResolution(
					leagueMatched.get(0),
					"TEAM_PAIR_SET_NUMBER_LEAGUE",
					new java.math.BigDecimal("0.9500"),
					false);
		}
		if (leagueMatched.size() > 1) {
			return GameResolution.conflictResult();
		}
		if (candidates.size() == 1) {
			return new GameResolution(
					candidates.get(0),
					"TEAM_PAIR_SET_NUMBER",
					new java.math.BigDecimal("0.9000"),
					false);
		}
		return GameResolution.conflictResult();
	}

	private List<Game> loadGamesByDate(LocalDate date) {
		LocalDateTime start = date.atStartOfDay();
		LocalDateTime end = date.plusDays(1).atStartOfDay().minusNanos(1);
		return gameRepository.findAllWithParticipantsByActualGameStartTimeBetween(start, end);
	}

	private Set<String> candidateInternalLeagueNames(String externalLeagueName) {
		String normalized = normalizeLeagueName(externalLeagueName);
		Set<String> aliases = GAME_LEAGUE_ALIASES.get(normalized);
		if (aliases == null || aliases.isEmpty()) {
			return Set.of(normalized);
		}
		return aliases;
	}

	private String normalizeLeagueName(String leagueName) {
		return leagueName == null ? "" : leagueName.trim().toUpperCase();
	}

	private String buildTeamPairKey(Game game) {
		Set<Long> teamIds = game.getParticipants().stream()
				.map(participant -> participant.getTeam().getId())
				.filter(java.util.Objects::nonNull)
				.collect(Collectors.toSet());
		if (teamIds.size() != 2) {
			return "";
		}
		List<Long> sorted = teamIds.stream().sorted().toList();
		return sorted.get(0) + ":" + sorted.get(1);
	}

	private String buildTeamPairKey(Long left, Long right) {
		if (left == null || right == null) {
			return "";
		}
		return left < right ? left + ":" + right : right + ":" + left;
	}

	private boolean hasGameIdentityMetadataChange(
			GameExternalIdentity identity,
			LeagueMatch match,
			LeagueMatchGame gameRow,
			GameResolution resolution) {
		if (!java.util.Objects.equals(identity.getExternalMatchId(), match.getId())) {
			return true;
		}
		if (!java.util.Objects.equals(identity.getExternalLeagueName(), match.getLeagueName())) {
			return true;
		}
		if (!java.util.Objects.equals(identity.getMatchDate(), match.getMatchDate().toLocalDate())) {
			return true;
		}
		if (!java.util.Objects.equals(identity.getGameOrder(), gameRow.getGameOrder())) {
			return true;
		}
		if (!java.util.Objects.equals(identity.getMatchedBy(), resolution.matchedBy())) {
			return true;
		}
		return !java.util.Objects.equals(identity.getConfidence(), resolution.confidence());
	}

	private boolean shouldTrackGameRow(LeagueMatch match, Integer gameOrder) {
		if (gameOrder == null) {
			return false;
		}
		if ("unstarted".equalsIgnoreCase(match.getState())) {
			return false;
		}
		if (!"completed".equalsIgnoreCase(match.getState())) {
			return true;
		}
		if (match.getBlueScore() == null || match.getRedScore() == null) {
			return true;
		}
		int playedSets = match.getBlueScore() + match.getRedScore();
		return playedSets <= 0 || gameOrder <= playedSets;
	}

	private record DirectTeamMetadataSyncResult(int updatedCount, boolean requiresFallback) {
	}

	public record TeamIdentityBackfillResult(
			List<String> leagues,
			int fetchedPages,
			int discoveredExternalTeams,
			int createdMappings,
			int updatedMappings,
			int unresolvedMappings,
			int conflicts) {
	}

	public record LeagueMatchExternalTeamIdBackfillResult(
			List<String> leagues,
			int targetMatches,
			int updatedMatches,
			int unresolvedMatches) {
	}

	public record GameExternalIdentityBackfillResult(
			List<String> leagues,
			int targetMatches,
			int targetGameRows,
			int createdMappings,
			int updatedMappings,
			int unresolvedMappings,
			int conflicts) {
	}

	private record MatchGameIdSyncResult(
			int targetMatches,
			int updatedMatches,
			int unchangedMatches,
			int failedMatches) {
	}

	private record MatchSyncUpsertResult(
			Set<String> dirtyMatchIds,
			int insertedMatches,
			int updatedMatches,
			int skippedMatches) {
	}

	private record AutoBackfillRecentMatchesResult(
			int pageMatchCount,
			TeamIdentityBackfillResult teamIdentityResult,
			MatchGameIdSyncResult gameIdSyncResult,
			GameExternalIdentityBackfillResult gameIdentityResult) {
	}

	public record RecentMatchBackfillResult(
			String league,
			int fetchedMatches,
			int insertedMatches,
			int updatedMatches,
			int skippedMatches,
			int metadataUpdated,
			int pageMatchCount,
			int teamIdentityCreated,
			int teamIdentityUpdated,
			int teamIdentityUnresolved,
			int gameIdUpdated,
			int gameIdUnchanged,
			int gameIdFailed,
			int gameIdentityCreated,
			int gameIdentityUpdated,
			int gameIdentityUnresolved,
			boolean includeTeamMetadata) {
	}

	private record ExternalTeamCandidate(
			String externalTeamId,
			String externalName,
			String externalCode,
			String externalImageUrl,
			String league) {
	}

	private record GameResolution(
			Game game,
			String matchedBy,
			java.math.BigDecimal confidence,
			boolean isConflict) {
		private static GameResolution unresolved() {
			return new GameResolution(null, null, null, false);
		}

		private static GameResolution conflictResult() {
			return new GameResolution(null, null, null, true);
		}
	}

	private Map<String, String> buildUniqueExternalIdMap(
			List<TeamExternalIdentity> identities,
			java.util.function.Function<TeamExternalIdentity, String> keyExtractor,
			boolean normalizeName) {
		Map<String, String> resolved = new HashMap<>();
		Set<String> ambiguous = new java.util.HashSet<>();

		for (TeamExternalIdentity identity : identities) {
			String rawKey = keyExtractor.apply(identity);
			if (rawKey == null || rawKey.isBlank()) {
				continue;
			}
			String key = normalizeName ? NameNormalizer.normalizeTeamName(rawKey) : rawKey;
			String existing = resolved.putIfAbsent(key, identity.getExternalTeamId());
			if (existing != null && !existing.equals(identity.getExternalTeamId())) {
				ambiguous.add(key);
			}
		}

		ambiguous.forEach(resolved::remove);
		return resolved;
	}

	private Map<String, String> buildUniqueCodeToExternalIdMap(List<TeamExternalIdentity> identities) {
		Map<String, String> resolved = new HashMap<>();
		Set<String> ambiguous = new java.util.HashSet<>();

		for (TeamExternalIdentity identity : identities) {
			String code = identity.getTeam().getCode();
			if (code == null || code.isBlank()) {
				continue;
			}
			String normalizedCode = code.trim().toUpperCase();
			String existing = resolved.putIfAbsent(normalizedCode, identity.getExternalTeamId());
			if (existing != null && !existing.equals(identity.getExternalTeamId())) {
				ambiguous.add(normalizedCode);
			}
		}

		ambiguous.forEach(resolved::remove);
		return resolved;
	}

	private String resolveLeagueMatchExternalTeamId(
			String teamName,
			String teamCode,
			Map<String, String> externalIdByExactExternalName,
			Map<String, String> externalIdByNormalizedExternalName,
			Map<String, String> externalIdByExactInternalName,
			Map<String, String> externalIdByNormalizedInternalName,
			Map<String, String> externalIdByCode) {
		if (teamName != null && !teamName.isBlank()) {
			String exactExternal = externalIdByExactExternalName.get(teamName);
			if (exactExternal != null) {
				return exactExternal;
			}

			String normalizedName = NameNormalizer.normalizeTeamName(teamName);
			String normalizedExternal = externalIdByNormalizedExternalName.get(normalizedName);
			if (normalizedExternal != null) {
				return normalizedExternal;
			}

			String exactInternal = externalIdByExactInternalName.get(teamName);
			if (exactInternal != null) {
				return exactInternal;
			}

			String normalizedInternal = externalIdByNormalizedInternalName.get(normalizedName);
			if (normalizedInternal != null) {
				return normalizedInternal;
			}
		}

		if (teamCode != null && !teamCode.isBlank()) {
			return externalIdByCode.get(teamCode.trim().toUpperCase());
		}

		return null;
	}

	private boolean isPlaceholderTeamName(String teamName) {
		if (teamName == null || teamName.isBlank()) {
			return true;
		}
		String normalized = teamName.trim().toUpperCase();
		return "TBD".equals(normalized) || "TBA".equals(normalized);
	}
}
