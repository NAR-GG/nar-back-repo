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

	Optional<LivePlayerRating> findByMatchIdAndPlayerRefAndMember_Id(
			String matchId,
			String playerRef,
			Long memberId);

	List<LivePlayerRating> findByMatchIdAndMember_Id(String matchId, Long memberId);

	@EntityGraph(attributePaths = "player")
	Page<LivePlayerRating> findByMember_IdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

	@EntityGraph(attributePaths = {"member", "member.favoriteTeam"})
	Page<LivePlayerRating> findByMatchIdAndPlayerRefOrderByCreatedAtDesc(
			String matchId,
			String playerRef,
			Pageable pageable);

	@Query("""
			SELECT r.playerRef AS playerRef,
			       AVG(r.rating) AS averageRating,
			       COUNT(r.id) AS ratingCount
			FROM LivePlayerRating r
			WHERE r.matchId = :matchId
			GROUP BY r.playerRef
			""")
	List<PlayerRatingAggregate> aggregateByMatchId(@Param("matchId") String matchId);

	@Query("""
			SELECT r.rating AS rating,
			       COUNT(r.id) AS ratingCount
			FROM LivePlayerRating r
			WHERE r.matchId = :matchId
			  AND r.playerRef = :playerRef
			GROUP BY r.rating
			""")
	List<RatingDistributionAggregate> distribution(
			@Param("matchId") String matchId,
			@Param("playerRef") String playerRef);

	interface PlayerRatingAggregate {
		String getPlayerRef();
		Double getAverageRating();
		Long getRatingCount();
	}

	interface RatingDistributionAggregate {
		Integer getRating();
		Long getRatingCount();
	}
}
