package com.toy.nar.app.mobile.rating;

import com.toy.nar.app.lolesports.live.LiveStateQueryService;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveParticipantState;
import com.toy.nar.app.lolesports.repository.LeagueMatchGame;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingDetailResponse;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingListResponse;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingRequest;
import com.toy.nar.app.mobile.rating.dto.MyRatingListResponse;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.participant.entity.Player;
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

	public LivePlayerRatingListResponse getRatings(String gameId, String teamSide, Long memberId) {
		LiveGameState state = requireState(gameId);
		Map<Integer, LivePlayerRatingRepository.ParticipantRatingAggregate> aggregates = ratingRepository
				.aggregateByGameId(gameId).stream()
				.collect(Collectors.toMap(
						LivePlayerRatingRepository.ParticipantRatingAggregate::getParticipantId,
						Function.identity()));
		Map<Integer, Integer> myRatings = memberId == null
				? Map.of()
				: ratingRepository.findByLiveGameIdAndMember_Id(gameId, memberId).stream()
						.collect(Collectors.toMap(LivePlayerRating::getLiveParticipantId, LivePlayerRating::getRating));
		Map<Integer, Player> players = resolvePlayers(state.participants());

		List<LivePlayerRatingListResponse.PlayerRatingSummary> summaries = state.participants().stream()
				.filter(participant -> teamSide == null
						|| "ALL".equalsIgnoreCase(teamSide)
						|| teamSide.equalsIgnoreCase(participant.teamSide()))
				.map(participant -> toSummary(participant, aggregates.get(participant.participantId()),
						players.get(participant.participantId()), myRatings.get(participant.participantId())))
				.toList();

		return new LivePlayerRatingListResponse(
				gameId,
				isRateable(gameId),
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
		Player player = resolvePlayer(participant).orElse(null);
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));
		Page<LivePlayerRating> ratings = ratingRepository
				.findByLiveGameIdAndLiveParticipantIdOrderByCreatedAtDesc(
						gameId,
						participantId,
						PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
		long total = ratings.getTotalElements();
		List<LivePlayerRatingRepository.RatingDistributionAggregate> distributionValues =
				ratingRepository.distribution(gameId, participantId);
		double average = averageFromDistribution(distributionValues);
		Map<Integer, Long> distribution = distributionValues.stream()
				.collect(Collectors.toMap(
						LivePlayerRatingRepository.RatingDistributionAggregate::getRating,
						LivePlayerRatingRepository.RatingDistributionAggregate::getRatingCount));
		LivePlayerRatingDetailResponse.MyRating myRating = memberId == null
				? null
				: ratingRepository.findByLiveGameIdAndLiveParticipantIdAndMember_Id(gameId, participantId, memberId)
						.map(this::toMyRating)
						.orElse(null);

		return new LivePlayerRatingDetailResponse(
				gameId,
				isRateable(gameId),
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
		if (!isRateable(gameId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "세트 종료 후 평가할 수 있습니다.");
		}
		LiveGameState state = requireState(gameId);
		LiveParticipantState participant = requireParticipant(state, participantId);
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
		Player player = resolvePlayer(participant).orElse(null);

		LivePlayerRating rating = ratingRepository
				.findByLiveGameIdAndLiveParticipantIdAndMember_Id(gameId, participantId, memberId)
				.orElseGet(() -> new LivePlayerRating(
						gameId,
						participantId,
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

		return new MyRatingListResponse(
				ratings.getContent().stream()
						.map(rating -> toMyRatingItem(rating, matchGamesByGameId.get(rating.getLiveGameId())))
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
		LivePlayerRating rating = ratingRepository
				.findByLiveGameIdAndLiveParticipantIdAndMember_Id(gameId, participantId, memberId)
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

	private boolean isRateable(String gameId) {
		Optional<LeagueMatchGame> matchGame = leagueMatchGameRepository.findWithMatchByGameId(gameId);
		if (matchGame.isEmpty()) {
			return false;
		}
		LeagueMatchGame game = matchGame.get();
		if ("completed".equalsIgnoreCase(game.getLeagueMatch().getState())) {
			return true;
		}
		Integer gameOrder = game.getGameOrder();
		Integer blueScore = game.getLeagueMatch().getBlueScore();
		Integer redScore = game.getLeagueMatch().getRedScore();
		return gameOrder != null
				&& blueScore != null
				&& redScore != null
				&& blueScore + redScore >= gameOrder;
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

	private LivePlayerRatingListResponse.PlayerRatingSummary toSummary(
			LiveParticipantState participant,
			LivePlayerRatingRepository.ParticipantRatingAggregate aggregate,
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
			Map<Integer, LivePlayerRatingRepository.ParticipantRatingAggregate> aggregates) {
		Map<String, TeamAccumulator> bySide = new LinkedHashMap<>();
		for (LiveParticipantState participant : state.participants()) {
			TeamAccumulator accumulator = bySide.computeIfAbsent(
					participant.teamSide(),
					side -> new TeamAccumulator(side, teamName(state, side)));
			LivePlayerRatingRepository.ParticipantRatingAggregate aggregate = aggregates.get(participant.participantId());
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

	private MyRatingListResponse.MyRatingItem toMyRatingItem(LivePlayerRating rating, LeagueMatchGame matchGame) {
		Player player = rating.getPlayer();
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
				toMatchInfo(matchGame));
	}

	private MyRatingListResponse.MatchInfo toMatchInfo(LeagueMatchGame matchGame) {
		if (matchGame == null) {
			return null;
		}
		return new MyRatingListResponse.MatchInfo(
				matchGame.getLeagueMatch().getId(),
				matchGame.getGameOrder(),
				matchGame.getLeagueMatch().getLeagueName(),
				matchGame.getLeagueMatch().getMatchTitle(),
				matchGame.getLeagueMatch().getBlueTeamCode(),
				matchGame.getLeagueMatch().getRedTeamCode(),
				toKst(matchGame.getLeagueMatch().getMatchDate()));
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
		return new LivePlayerRatingDetailResponse.Review(
				rating.getId(),
				rating.getMember().getNickname(),
				rating.getRating(),
				rating.getComment(),
				memberId != null && memberId.equals(rating.getMember().getId()),
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
