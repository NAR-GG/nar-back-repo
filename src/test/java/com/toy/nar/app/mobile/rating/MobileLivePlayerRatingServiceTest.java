package com.toy.nar.app.mobile.rating;

import com.toy.nar.app.lolesports.live.LiveStateQueryService;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveParticipantState;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchGame;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingListResponse;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingDetailResponse;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingRequest;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.rating.entity.LivePlayerRating;
import com.toy.nar.domain.rating.repository.LivePlayerRatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MobileLivePlayerRatingServiceTest {

	private LiveStateQueryService liveStateQueryService;
	private LivePlayerRatingRepository ratingRepository;
	private MemberRepository memberRepository;
	private PlayerRepository playerRepository;
	private LeagueMatchGameRepository leagueMatchGameRepository;
	private MobileLivePlayerRatingService service;

	@BeforeEach
	void setUp() {
		liveStateQueryService = mock(LiveStateQueryService.class);
		ratingRepository = mock(LivePlayerRatingRepository.class);
		memberRepository = mock(MemberRepository.class);
		playerRepository = mock(PlayerRepository.class);
		leagueMatchGameRepository = mock(LeagueMatchGameRepository.class);
		service = new MobileLivePlayerRatingService(
				liveStateQueryService,
				ratingRepository,
				memberRepository,
				playerRepository,
				leagueMatchGameRepository);
	}

	@Test
	void cannotRateBeforeSetEnds() {
		when(leagueMatchGameRepository.findWithMatchByGameId("game-1"))
				.thenReturn(Optional.of(matchGame("inProgress", 0, 0, 1)));

		assertThatThrownBy(() -> service.save(
				"game-1",
				1,
				7L,
				new LivePlayerRatingRequest(5, "좋은 경기")))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("409 CONFLICT");

		verify(ratingRepository, never()).save(any());
	}

	@Test
	void createsRatingAfterSetEnds() {
		Member member = Member.builder().name("용맹한바론").tag("0000").email("test@example.com").build();
		Player player = Player.builder().name("Faker").imageUrl("faker.png").build();
		when(leagueMatchGameRepository.findWithMatchByGameId("game-1"))
				.thenReturn(Optional.of(matchGame("inProgress", 1, 0, 1)));
		when(liveStateQueryService.getLatestState("game-1")).thenReturn(Optional.of(state()));
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(playerRepository.findByPlayerOriginId("oe:player:faker")).thenReturn(Optional.of(player));
		when(ratingRepository.findByLiveGameIdAndLiveParticipantIdAndMember_Id("game-1", 1, 7L))
				.thenReturn(Optional.empty());
		when(ratingRepository.save(any(LivePlayerRating.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		var response = service.save(
				"game-1",
				1,
				7L,
				new LivePlayerRatingRequest(5, "캐리했습니다"));

		assertThat(response.rating()).isEqualTo(5);
		assertThat(response.comment()).isEqualTo("캐리했습니다");
		verify(ratingRepository).save(any(LivePlayerRating.class));
	}

	@Test
	void returnsTeamAndPlayerRatingSummaries() {
		LivePlayerRatingRepository.ParticipantRatingAggregate aggregate = mock(
				LivePlayerRatingRepository.ParticipantRatingAggregate.class);
		when(aggregate.getParticipantId()).thenReturn(1);
		when(aggregate.getAverageRating()).thenReturn(4.5);
		when(aggregate.getRatingCount()).thenReturn(2L);
		when(liveStateQueryService.getLatestState("game-1")).thenReturn(Optional.of(state()));
		when(ratingRepository.aggregateByGameId("game-1")).thenReturn(List.of(aggregate));
		when(leagueMatchGameRepository.findWithMatchByGameId("game-1"))
				.thenReturn(Optional.of(matchGame("completed", 2, 1, 1)));
		when(playerRepository.findByPlayerOriginId("oe:player:faker")).thenReturn(Optional.empty());
		when(playerRepository.findByName("Faker")).thenReturn(Optional.empty());

		LivePlayerRatingListResponse response = service.getRatings("game-1", "ALL", null);

		assertThat(response.rateable()).isTrue();
		assertThat(response.players()).hasSize(1);
		assertThat(response.players().get(0).averageRating()).isEqualTo(4.5);
		assertThat(response.teams()).hasSize(1);
		assertThat(response.teams().get(0).averageRating()).isEqualTo(4.5);
		assertThat(response.teams().get(0).ratingCount()).isEqualTo(2);
	}

	@Test
	void returnsRatingDistributionReviewsAndMyRating() {
		Member member = member(7L, "용맹한바론");
		ReflectionTestUtils.setField(member, "profileImageUrl", "https://cdn/profile/7.png");
		Team favoriteTeam = new Team("T1", "T1", "https://cdn/team/t1.png");
		ReflectionTestUtils.setField(favoriteTeam, "id", 3L);
		ReflectionTestUtils.setField(member, "favoriteTeam", favoriteTeam);
		LivePlayerRating rating = rating(member, 5, "역시 페이커");
		ReflectionTestUtils.setField(rating, "id", 11L);

		LivePlayerRatingRepository.RatingDistributionAggregate fiveStars = distribution(5, 3L);
		LivePlayerRatingRepository.RatingDistributionAggregate fourStars = distribution(4, 1L);
		when(liveStateQueryService.getLatestState("game-1")).thenReturn(Optional.of(state()));
		when(playerRepository.findByPlayerOriginId("oe:player:faker")).thenReturn(Optional.empty());
		when(playerRepository.findByName("Faker")).thenReturn(Optional.empty());
		when(ratingRepository.findByLiveGameIdAndLiveParticipantIdOrderByCreatedAtDesc(
				"game-1", 1, PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(
						org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))))
				.thenReturn(new PageImpl<>(List.of(rating, rating, rating, rating), PageRequest.of(0, 20), 4));
		when(ratingRepository.distribution("game-1", 1)).thenReturn(List.of(fiveStars, fourStars));
		when(ratingRepository.findByLiveGameIdAndLiveParticipantIdAndMember_Id("game-1", 1, 7L))
				.thenReturn(Optional.of(rating));
		when(leagueMatchGameRepository.findWithMatchByGameId("game-1"))
				.thenReturn(Optional.of(matchGame("completed", 2, 1, 1)));

		LivePlayerRatingDetailResponse response = service.getDetail("game-1", 1, 7L, 0, 20);

		assertThat(response.averageRating()).isEqualTo(4.8);
		assertThat(response.ratingCount()).isEqualTo(4);
		assertThat(response.distribution()).extracting(
				LivePlayerRatingDetailResponse.RatingDistribution::rating,
				LivePlayerRatingDetailResponse.RatingDistribution::count,
				LivePlayerRatingDetailResponse.RatingDistribution::percentage)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(5, 3L, 75.0),
						org.assertj.core.groups.Tuple.tuple(4, 1L, 25.0),
						org.assertj.core.groups.Tuple.tuple(3, 0L, 0.0),
						org.assertj.core.groups.Tuple.tuple(2, 0L, 0.0),
						org.assertj.core.groups.Tuple.tuple(1, 0L, 0.0));
		assertThat(response.myRating().ratingId()).isEqualTo(11L);
		assertThat(response.reviews()).first().satisfies(review -> {
			assertThat(review.nickname()).isEqualTo("용맹한바론#0000");
			assertThat(review.mine()).isTrue();
			assertThat(review.profileImageUrl()).isEqualTo("https://cdn/profile/7.png");
			assertThat(review.favoriteTeamId()).isEqualTo(3L);
			assertThat(review.teamImageUrl()).isEqualTo("https://cdn/team/t1.png");
		});
	}

	@Test
	void updatesExistingRatingAndDeletesIt() {
		Member member = member(7L, "용맹한바론");
		LivePlayerRating existing = rating(member, 3, "무난했습니다");
		when(leagueMatchGameRepository.findWithMatchByGameId("game-1"))
				.thenReturn(Optional.of(matchGame("completed", 2, 1, 1)));
		when(liveStateQueryService.getLatestState("game-1")).thenReturn(Optional.of(state()));
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(playerRepository.findByPlayerOriginId("oe:player:faker")).thenReturn(Optional.empty());
		when(playerRepository.findByName("Faker")).thenReturn(Optional.empty());
		when(ratingRepository.findByLiveGameIdAndLiveParticipantIdAndMember_Id("game-1", 1, 7L))
				.thenReturn(Optional.of(existing));
		when(ratingRepository.save(existing)).thenReturn(existing);

		var updated = service.save("game-1", 1, 7L, new LivePlayerRatingRequest(5, "생각이 바뀌었습니다"));
		service.delete("game-1", 1, 7L);

		assertThat(updated.rating()).isEqualTo(5);
		assertThat(updated.comment()).isEqualTo("생각이 바뀌었습니다");
		verify(ratingRepository).delete(existing);
	}

	@Test
	void getMyRatingsRequiresLogin() {
		assertThatThrownBy(() -> service.getMyRatings(null, 0, 20))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("401 UNAUTHORIZED");
	}

	@Test
	void getMyRatingsReturnsItemsWithMatchInfo() {
		Member member = member(7L, "용맹한바론");
		LivePlayerRating myRating = rating(member, 5, "역시 페이커");
		ReflectionTestUtils.setField(myRating, "id", 11L);
		ReflectionTestUtils.setField(myRating, "createdAt", LocalDateTime.of(2026, 6, 6, 13, 0));
		ReflectionTestUtils.setField(myRating, "updatedAt", LocalDateTime.of(2026, 6, 6, 13, 0));
		LeagueMatch match = LeagueMatch.builder()
				.id("match-1")
				.leagueName("LCK")
				.matchTitle("DNS vs T1")
				.matchDate(LocalDateTime.of(2026, 6, 6, 9, 0))
				.state("completed")
				.blueTeamCode("DNS")
				.redTeamCode("T1")
				.build();
		when(ratingRepository.findByMember_IdOrderByCreatedAtDesc(7L, PageRequest.of(0, 20)))
				.thenReturn(new PageImpl<>(List.of(myRating), PageRequest.of(0, 20), 1));
		when(leagueMatchGameRepository.findAllWithMatchByGameIdIn(java.util.Set.of("game-1")))
				.thenReturn(List.of(new LeagueMatchGame(match, "game-1", 2)));

		var response = service.getMyRatings(7L, 0, 20);

		assertThat(response.totalElements()).isEqualTo(1);
		assertThat(response.ratings()).singleElement().satisfies(item -> {
			assertThat(item.ratingId()).isEqualTo(11L);
			assertThat(item.gameId()).isEqualTo("game-1");
			assertThat(item.playerName()).isEqualTo("Faker");
			assertThat(item.championName()).isEqualTo("Ahri");
			assertThat(item.rating()).isEqualTo(5);
			assertThat(item.comment()).isEqualTo("역시 페이커");
			assertThat(item.match().matchId()).isEqualTo("match-1");
			assertThat(item.match().gameOrder()).isEqualTo(2);
			assertThat(item.match().blueTeamCode()).isEqualTo("DNS");
			assertThat(item.match().matchDate()).isEqualTo(LocalDateTime.of(2026, 6, 6, 18, 0));
		});
	}

	@Test
	void getMyRatingsReturnsNullMatchWhenMappingMissing() {
		Member member = member(7L, "용맹한바론");
		LivePlayerRating myRating = rating(member, 4, null);
		when(ratingRepository.findByMember_IdOrderByCreatedAtDesc(7L, PageRequest.of(0, 20)))
				.thenReturn(new PageImpl<>(List.of(myRating), PageRequest.of(0, 20), 1));
		when(leagueMatchGameRepository.findAllWithMatchByGameIdIn(java.util.Set.of("game-1")))
				.thenReturn(List.of());

		var response = service.getMyRatings(7L, 0, 20);

		assertThat(response.ratings()).singleElement()
				.satisfies(item -> assertThat(item.match()).isNull());
	}

	private Member member(Long id, String nickname) {
		Member member = Member.builder().name(nickname).tag("0000").email("test@example.com").build();
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	private LivePlayerRating rating(Member member, int score, String comment) {
		return new LivePlayerRating(
				"game-1",
				1,
				member,
				null,
				"Red",
				"mid",
				"Faker",
				"oe:player:faker",
				"Ahri",
				score,
				comment);
	}

	private LivePlayerRatingRepository.RatingDistributionAggregate distribution(int score, long count) {
		LivePlayerRatingRepository.RatingDistributionAggregate aggregate = mock(
				LivePlayerRatingRepository.RatingDistributionAggregate.class);
		when(aggregate.getRating()).thenReturn(score);
		when(aggregate.getRatingCount()).thenReturn(count);
		return aggregate;
	}

	private LiveGameState state() {
		return new LiveGameState(
				"game-1",
				"match-1",
				"LCK",
				"DNS",
				"T1",
				LocalDateTime.of(2026, 6, 6, 12, 30),
				LocalDateTime.of(2026, 6, 6, 12, 30, 10),
				List.of(new LiveParticipantState(
						1,
						"Red",
						"mid",
						"Faker",
						"oe:player:faker",
						"Ahri",
						18,
						4,
						1,
						7,
						15000,
						280,
						0.7,
						0.3,
						List.of(),
						List.of(),
						List.of(),
						"{}",
						null,
						null,
						List.of())),
				List.of());
	}

	private LeagueMatchGame matchGame(String state, int blueScore, int redScore, int gameOrder) {
		LeagueMatch match = LeagueMatch.builder()
				.id("match-1")
				.leagueName("LCK")
				.state(state)
				.blueScore(blueScore)
				.redScore(redScore)
				.build();
		return new LeagueMatchGame(match, "game-1", gameOrder);
	}
}
