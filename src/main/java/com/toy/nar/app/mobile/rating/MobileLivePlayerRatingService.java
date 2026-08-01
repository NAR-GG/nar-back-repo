package com.toy.nar.app.mobile.rating;

import com.toy.nar.app.lolesports.live.LiveStateQueryService;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveParticipantState;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchGame;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.schedule.MobileScheduleService;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleListResponse;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingDetailResponse;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingListResponse;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingRequest;
import com.toy.nar.app.mobile.rating.dto.MyRatingListResponse;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.rating.entity.LivePlayerRating;
import com.toy.nar.domain.rating.repository.LivePlayerRatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileLivePlayerRatingService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final ZoneId UTC = ZoneId.of("UTC");

	private final LiveStateQueryService liveStateQueryService;
	private final LivePlayerRatingRepository ratingRepository;
	private final MemberRepository memberRepository;
	private final PlayerRepository playerRepository;
	private final LeagueMatchGameRepository leagueMatchGameRepository;
	private final LeagueMatchRepository leagueMatchRepository;
	private final MobileScheduleService mobileScheduleService;

	public LivePlayerRatingListResponse getRatings(String gameId, String teamSide, Long memberId) {
		LiveGameState state = requireState(gameId);
		String matchId = state.matchId();
		Map<String, LivePlayerRatingRepository.PlayerRatingAggregate> aggregates = ratingRepository
				.aggregateByMatchId(matchId).stream()
				.collect(Collectors.toMap(
						LivePlayerRatingRepository.PlayerRatingAggregate::getPlayerRef,
						Function.identity()));
		Map<String, Integer> myRatings = memberId == null
				? Map.of()
				: ratingRepository.findByMatchIdAndMember_Id(matchId, memberId).stream()
						.collect(Collectors.toMap(LivePlayerRating::getPlayerRef, LivePlayerRating::getRating,
								(left, right) -> left));
		Map<Integer, Player> players = resolvePlayers(state.participants());

		List<LivePlayerRatingListResponse.PlayerRatingSummary> summaries = state.participants().stream()
				.filter(participant -> teamSide == null
						|| "ALL".equalsIgnoreCase(teamSide)
						|| teamSide.equalsIgnoreCase(participant.teamSide()))
				.map(participant -> toSummary(participant, aggregates.get(playerRef(participant)),
						players.get(participant.participantId()), myRatings.get(playerRef(participant))))
				.toList();

		return new LivePlayerRatingListResponse(
				gameId,
				true,
				buildTeamSummaries(state, aggregates),
				summaries);
	}

	public LivePlayerRatingDetailResponse getDetail(
			String gameId,
			Integer participantId,
			Long memberId,
			int page,
			int size) {
		LiveGameState state = requireState(gameId);
		LiveParticipantState participant = requireParticipant(state, participantId);
		String matchId = state.matchId();
		String playerRef = playerRef(participant);
		Player player = resolvePlayer(participant).orElse(null);
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));
		Page<LivePlayerRating> ratings = ratingRepository
				.findByMatchIdAndPlayerRefOrderByCreatedAtDesc(
						matchId,
						playerRef,
						PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
		long total = ratings.getTotalElements();
		List<LivePlayerRatingRepository.RatingDistributionAggregate> distributionValues =
				ratingRepository.distribution(matchId, playerRef);
		double average = averageFromDistribution(distributionValues);
		Map<Integer, Long> distribution = distributionValues.stream()
				.collect(Collectors.toMap(
						LivePlayerRatingRepository.RatingDistributionAggregate::getRating,
						LivePlayerRatingRepository.RatingDistributionAggregate::getRatingCount));
		LivePlayerRatingDetailResponse.MyRating myRating = memberId == null
				? null
				: ratingRepository.findByMatchIdAndPlayerRefAndMember_Id(matchId, playerRef, memberId)
						.map(this::toMyRating)
						.orElse(null);

		return new LivePlayerRatingDetailResponse(
				gameId,
				true,
				new LivePlayerRatingDetailResponse.PlayerHeader(
						participant.participantId(),
						player != null ? player.getId() : null,
						participant.playerName(),
						player != null ? player.getImageUrl() : null,
						participant.teamSide(),
						participant.role(),
						participant.championName(),
						participant.kills(),
						participant.deaths(),
						participant.assists()),
				round(average),
				total,
				buildDistribution(distribution, total),
				myRating,
				ratings.getContent().stream().map(rating -> toReview(rating, memberId)).toList(),
				ratings.getNumber(),
				ratings.getSize(),
				total,
				ratings.getTotalPages());
	}

	@Transactional
	public LivePlayerRatingDetailResponse.MyRating save(
			String gameId,
			Integer participantId,
			Long memberId,
			LivePlayerRatingRequest request) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		// 라이브 경기 중에도 평가 허용(세트 종료 제한 해제). 평가는 라이브 상태가 존재하면 언제든 가능.
		LiveGameState state = requireState(gameId);
		LiveParticipantState participant = requireParticipant(state, participantId);
		String matchId = state.matchId();
		String playerRef = playerRef(participant);
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
		Player player = resolvePlayer(participant).orElse(null);

		LivePlayerRating rating = ratingRepository
				.findByMatchIdAndPlayerRefAndMember_Id(matchId, playerRef, memberId)
				.orElseGet(() -> new LivePlayerRating(
						matchId,
						gameId,
						participantId,
						playerRef,
						member,
						player,
						participant.teamSide(),
						participant.role(),
						participant.playerName(),
						participant.esportsPlayerId(),
						participant.championName(),
						request.rating(),
						request.comment()));
		rating.update(request.rating(), request.comment());
		return toMyRating(ratingRepository.save(rating));
	}

	public MyRatingListResponse getMyRatings(Long memberId, int page, int size) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));
		Page<LivePlayerRating> ratings = ratingRepository.findByMember_IdOrderByCreatedAtDesc(
				memberId,
				PageRequest.of(safePage, safeSize));

		Set<String> gameIds = ratings.getContent().stream()
				.map(LivePlayerRating::getLiveGameId)
				.collect(Collectors.toSet());
		Map<String, LeagueMatchGame> matchGamesByGameId = gameIds.isEmpty()
				? Map.of()
				: leagueMatchGameRepository.findAllWithMatchByGameIdIn(gameIds).stream()
						.collect(Collectors.toMap(LeagueMatchGame::getGameId, Function.identity(), (left, right) -> left));
		// league_match_game 동기화 구멍(마지막 세트 누락 등)이 있어도 경기상세와 동일하게 보이도록,
		// 미매핑 게임은 경기상세 세트 목록 로직(라이브 스냅샷 보강 포함)으로 matchInfo를 만든다.
		Map<String, MyRatingListResponse.MatchInfo> fallbackMatchInfoByGameId =
				buildFallbackMatchInfo(ratings.getContent(), matchGamesByGameId.keySet());

		return new MyRatingListResponse(
				ratings.getContent().stream()
						.map(rating -> toMyRatingItem(
								rating,
								matchGamesByGameId.get(rating.getLiveGameId()),
								fallbackMatchInfoByGameId.get(rating.getLiveGameId())))
						.toList(),
				ratings.getNumber(),
				ratings.getSize(),
				ratings.getTotalElements(),
				ratings.getTotalPages());
	}

	@Transactional
	public void delete(String gameId, Integer participantId, Long memberId) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		LiveGameState state = requireState(gameId);
		LiveParticipantState participant = requireParticipant(state, participantId);
		LivePlayerRating rating = ratingRepository
				.findByMatchIdAndPlayerRefAndMember_Id(state.matchId(), playerRef(participant), memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "내 평가를 찾을 수 없습니다."));
		ratingRepository.delete(rating);
	}

	private LiveGameState requireState(String gameId) {
		return liveStateQueryService.getLatestState(gameId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "라이브 경기 정보를 찾을 수 없습니다."));
	}

	private LiveParticipantState requireParticipant(LiveGameState state, Integer participantId) {
		return state.participants().stream()
				.filter(participant -> participant.participantId().equals(participantId))
				.findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "선수 정보를 찾을 수 없습니다."));
	}

	private Map<Integer, Player> resolvePlayers(List<LiveParticipantState> participants) {
		Map<Integer, Player> resolved = new HashMap<>();
		for (LiveParticipantState participant : participants) {
			resolvePlayer(participant).ifPresent(player -> resolved.put(participant.participantId(), player));
		}
		return resolved;
	}

	private Optional<Player> resolvePlayer(LiveParticipantState participant) {
		if (participant.esportsPlayerId() != null && !participant.esportsPlayerId().isBlank()) {
			Optional<Player> byOriginId = playerRepository.findByPlayerOriginId(participant.esportsPlayerId());
			if (byOriginId.isPresent()) {
				return byOriginId;
			}
		}
		String name = participant.playerName();
		if (name == null || name.isBlank()) {
			return Optional.empty();
		}
		Optional<Player> byName = playerRepository.findByName(name);
		if (byName.isPresent()) {
			return byName;
		}
		// 라이브 피드의 참가자명이 "팀코드 소환사명"(예: "HLE Zeus") 형태라
		// Player.name("Zeus")과 안 맞는다. 마지막 토큰(소환사명)으로 한 번 더 매칭한다.
		int lastSpace = name.lastIndexOf(' ');
		if (lastSpace > 0 && lastSpace < name.length() - 1) {
			return playerRepository.findByName(name.substring(lastSpace + 1).trim());
		}
		return Optional.empty();
	}

	private String playerRef(LiveParticipantState participant) {
		String esportsPlayerId = participant.esportsPlayerId();
		if (esportsPlayerId != null && !esportsPlayerId.isBlank()) {
			return esportsPlayerId;
		}
		return "name:" + participant.playerName();
	}

	private LivePlayerRatingListResponse.PlayerRatingSummary toSummary(
			LiveParticipantState participant,
			LivePlayerRatingRepository.PlayerRatingAggregate aggregate,
			Player player,
			Integer myRating) {
		return new LivePlayerRatingListResponse.PlayerRatingSummary(
				participant.participantId(),
				player != null ? player.getId() : null,
				participant.playerName(),
				player != null ? player.getImageUrl() : null,
				participant.teamSide(),
				participant.role(),
				participant.championName(),
				aggregate != null ? round(aggregate.getAverageRating()) : 0.0,
				aggregate != null ? aggregate.getRatingCount() : 0,
				myRating);
	}

	private List<LivePlayerRatingListResponse.TeamRatingSummary> buildTeamSummaries(
			LiveGameState state,
			Map<String, LivePlayerRatingRepository.PlayerRatingAggregate> aggregates) {
		Map<String, TeamAccumulator> bySide = new LinkedHashMap<>();
		for (LiveParticipantState participant : state.participants()) {
			TeamAccumulator accumulator = bySide.computeIfAbsent(
					participant.teamSide(),
					side -> new TeamAccumulator(side, teamName(state, side)));
			LivePlayerRatingRepository.PlayerRatingAggregate aggregate = aggregates.get(playerRef(participant));
			if (aggregate != null) {
				accumulator.ratingSum += aggregate.getAverageRating() * aggregate.getRatingCount();
				accumulator.ratingCount += aggregate.getRatingCount();
			}
		}
		return bySide.values().stream()
				.map(accumulator -> new LivePlayerRatingListResponse.TeamRatingSummary(
						accumulator.side,
						accumulator.teamName,
						accumulator.ratingCount == 0 ? 0.0 : round(accumulator.ratingSum / accumulator.ratingCount),
						accumulator.ratingCount))
				.toList();
	}

	private String teamName(LiveGameState state, String side) {
		return "Blue".equalsIgnoreCase(side) ? state.blueTeamName() : state.redTeamName();
	}

	private List<LivePlayerRatingDetailResponse.RatingDistribution> buildDistribution(
			Map<Integer, Long> counts,
			long total) {
		List<LivePlayerRatingDetailResponse.RatingDistribution> result = new ArrayList<>();
		for (int rating = 5; rating >= 1; rating--) {
			long count = counts.getOrDefault(rating, 0L);
			double percentage = total == 0 ? 0.0 : round(count * 100.0 / total);
			result.add(new LivePlayerRatingDetailResponse.RatingDistribution(rating, count, percentage));
		}
		return result;
	}

	private double averageFromDistribution(
			List<LivePlayerRatingRepository.RatingDistributionAggregate> values) {
		long count = values.stream().mapToLong(LivePlayerRatingRepository.RatingDistributionAggregate::getRatingCount).sum();
		if (count == 0) {
			return 0.0;
		}
		double sum = values.stream()
				.mapToDouble(value -> value.getRating() * value.getRatingCount())
				.sum();
		return sum / count;
	}

	private MyRatingListResponse.MyRatingItem toMyRatingItem(
			LivePlayerRating rating,
			LeagueMatchGame matchGame,
			MyRatingListResponse.MatchInfo fallbackMatchInfo) {
		Player player = rating.getPlayer();
		Member member = rating.getMember();
		Team favoriteTeam = member != null ? member.getFavoriteTeam() : null;
		return new MyRatingListResponse.MyRatingItem(
				rating.getId(),
				rating.getLiveGameId(),
				rating.getLiveParticipantId(),
				player != null ? player.getId() : null,
				rating.getPlayerName(),
				player != null ? player.getImageUrl() : null,
				rating.getTeamSide(),
				rating.getRole(),
				rating.getChampionName(),
				rating.getRating(),
				rating.getComment(),
				rating.getCreatedAt(),
				rating.getUpdatedAt(),
				member != null ? member.getProfileImageUrl() : null,
				favoriteTeam != null ? favoriteTeam.getImageUrl() : null,
				matchGame != null ? toMatchInfo(matchGame) : fallbackMatchInfo);
	}

	private MyRatingListResponse.MatchInfo toMatchInfo(LeagueMatchGame matchGame) {
		return toMatchInfo(matchGame.getLeagueMatch(), matchGame.getGameOrder());
	}

	private MyRatingListResponse.MatchInfo toMatchInfo(LeagueMatch match, Integer gameOrder) {
		return new MyRatingListResponse.MatchInfo(
				match.getId(),
				gameOrder,
				match.getLeagueName(),
				match.getMatchTitle(),
				match.getBlueTeamCode(),
				match.getRedTeamCode(),
				toKst(match.getMatchDate()));
	}

	/**
	 * 매핑 테이블에 없는 게임의 matchInfo 폴백. 리뷰 행이 이미 matchId를 알고 있으므로
	 * 매치는 직접 조회하고, 세트 번호는 경기상세 세트 목록(getMatchGames)에서 찾는다 —
	 * 화면 간 세트 번호가 항상 일치한다.
	 */
	private Map<String, MyRatingListResponse.MatchInfo> buildFallbackMatchInfo(
			List<LivePlayerRating> ratings,
			Set<String> mappedGameIds) {
		Map<String, List<String>> gameIdsByMatchId = ratings.stream()
				.filter(rating -> !mappedGameIds.contains(rating.getLiveGameId()))
				.filter(rating -> rating.getMatchId() != null && !rating.getMatchId().isBlank())
				.collect(Collectors.groupingBy(LivePlayerRating::getMatchId,
						Collectors.mapping(LivePlayerRating::getLiveGameId,
								Collectors.collectingAndThen(Collectors.toSet(), List::copyOf))));
		if (gameIdsByMatchId.isEmpty()) {
			return Map.of();
		}
		Map<String, MyRatingListResponse.MatchInfo> result = new HashMap<>();
		for (Map.Entry<String, List<String>> entry : gameIdsByMatchId.entrySet()) {
			Optional<LeagueMatch> match = leagueMatchRepository.findById(entry.getKey());
			if (match.isEmpty()) {
				continue;
			}
			Map<String, Integer> orderByGameId = mobileScheduleService.getMatchGames(entry.getKey())
					.games().stream()
					.filter(game -> game.gameOrder() != null)
					.collect(Collectors.toMap(
							MobileScheduleListResponse.MobileGameSummary::gameId,
							MobileScheduleListResponse.MobileGameSummary::gameOrder,
							(left, right) -> left));
			for (String gameId : entry.getValue()) {
				result.put(gameId, toMatchInfo(match.get(), orderByGameId.get(gameId)));
			}
		}
		return result;
	}

	private LocalDateTime toKst(LocalDateTime utcDateTime) {
		if (utcDateTime == null) {
			return null;
		}
		return utcDateTime.atZone(UTC).withZoneSameInstant(KST).toLocalDateTime();
	}

	private LivePlayerRatingDetailResponse.MyRating toMyRating(LivePlayerRating rating) {
		return new LivePlayerRatingDetailResponse.MyRating(
				rating.getId(),
				rating.getRating(),
				rating.getComment());
	}

	private LivePlayerRatingDetailResponse.Review toReview(LivePlayerRating rating, Long memberId) {
		Member member = rating.getMember();
		Team favoriteTeam = member.getFavoriteTeam();
		return new LivePlayerRatingDetailResponse.Review(
				rating.getId(),
				member.getNickname(),
				member.getProfileImageUrl(),
				favoriteTeam != null ? favoriteTeam.getId() : null,
				favoriteTeam != null ? favoriteTeam.getImageUrl() : null,
				rating.getRating(),
				rating.getComment(),
				memberId != null && memberId.equals(member.getId()),
				rating.getCreatedAt(),
				rating.getUpdatedAt());
	}

	private double round(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

	private static final class TeamAccumulator {
		private final String side;
		private final String teamName;
		private double ratingSum;
		private long ratingCount;

		private TeamAccumulator(String side, String teamName) {
			this.side = side;
			this.teamName = teamName;
		}
	}
}
