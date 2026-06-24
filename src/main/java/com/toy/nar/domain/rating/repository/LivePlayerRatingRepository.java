package com.toy.nar.domain.rating.repository;

import com.toy.nar.domain.rating.entity.LivePlayerRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LivePlayerRatingRepository extends JpaRepository<LivePlayerRating, Long> {

	Optional<LivePlayerRating> findByLiveGameIdAndLiveParticipantIdAndMember_Id(
			String liveGameId,
			Integer liveParticipantId,
			Long memberId);

	List<LivePlayerRating> findByLiveGameIdAndMember_Id(String liveGameId, Long memberId);

	@EntityGraph(attributePaths = "player")
	Page<LivePlayerRating> findByMember_IdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

	@EntityGraph(attributePaths = {"member", "member.favoriteTeam"})
	Page<LivePlayerRating> findByLiveGameIdAndLiveParticipantIdOrderByCreatedAtDesc(
			String liveGameId,
			Integer liveParticipantId,
			Pageable pageable);

	@Query("""
			SELECT r.liveParticipantId AS participantId,
			       AVG(r.rating) AS averageRating,
			       COUNT(r.id) AS ratingCount
			FROM LivePlayerRating r
			WHERE r.liveGameId = :gameId
			GROUP BY r.liveParticipantId
			""")
	List<ParticipantRatingAggregate> aggregateByGameId(@Param("gameId") String gameId);

	@Query("""
			SELECT r.rating AS rating,
			       COUNT(r.id) AS ratingCount
			FROM LivePlayerRating r
			WHERE r.liveGameId = :gameId
			  AND r.liveParticipantId = :participantId
			GROUP BY r.rating
			""")
	List<RatingDistributionAggregate> distribution(
			@Param("gameId") String gameId,
			@Param("participantId") Integer participantId);

	interface ParticipantRatingAggregate {
		Integer getParticipantId();
		Double getAverageRating();
		Long getRatingCount();
	}

	interface RatingDistributionAggregate {
		Integer getRating();
		Long getRatingCount();
	}
}
