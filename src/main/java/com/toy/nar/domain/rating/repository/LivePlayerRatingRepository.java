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

	/**
	 * 백오피스 리뷰 목록. field(player|member|comment|all) + q 로 검색, rating = 별점 정확일치(모두 optional).
	 * member 는 join fetch(행마다 닉네임 필요) — count 쿼리는 fetch 없이 별도 지정.
	 */
	@Query(value = """
			SELECT r FROM LivePlayerRating r
			JOIN FETCH r.member m
			WHERE (:q IS NULL
			       OR (:field = 'player' AND LOWER(r.playerName) LIKE LOWER(CONCAT('%', :q, '%')))
			       OR (:field = 'member' AND LOWER(CONCAT(m.name, '#', m.tag)) LIKE LOWER(CONCAT('%', :q, '%')))
			       OR (:field = 'comment' AND LOWER(r.comment) LIKE LOWER(CONCAT('%', :q, '%')))
			       OR (:field = 'all' AND (LOWER(r.playerName) LIKE LOWER(CONCAT('%', :q, '%'))
			                               OR LOWER(CONCAT(m.name, '#', m.tag)) LIKE LOWER(CONCAT('%', :q, '%'))
			                               OR LOWER(r.comment) LIKE LOWER(CONCAT('%', :q, '%')))))
			  AND (:rating IS NULL OR r.rating = :rating)
			""",
			countQuery = """
			SELECT COUNT(r) FROM LivePlayerRating r
			JOIN r.member m
			WHERE (:q IS NULL
			       OR (:field = 'player' AND LOWER(r.playerName) LIKE LOWER(CONCAT('%', :q, '%')))
			       OR (:field = 'member' AND LOWER(CONCAT(m.name, '#', m.tag)) LIKE LOWER(CONCAT('%', :q, '%')))
			       OR (:field = 'comment' AND LOWER(r.comment) LIKE LOWER(CONCAT('%', :q, '%')))
			       OR (:field = 'all' AND (LOWER(r.playerName) LIKE LOWER(CONCAT('%', :q, '%'))
			                               OR LOWER(CONCAT(m.name, '#', m.tag)) LIKE LOWER(CONCAT('%', :q, '%'))
			                               OR LOWER(r.comment) LIKE LOWER(CONCAT('%', :q, '%')))))
			  AND (:rating IS NULL OR r.rating = :rating)
			""")
	Page<LivePlayerRating> searchForBackoffice(
			@Param("q") String q,
			@Param("field") String field,
			@Param("rating") Integer rating,
			Pageable pageable);

	@Query("""
			SELECT r.liveParticipantId AS participantId,
			       AVG(r.rating) AS averageRating,
			       COUNT(r.id) AS ratingCount
			FROM LivePlayerRating r
			WHERE r.liveGameId = :liveGameId
			GROUP BY r.liveParticipantId
			""")
	List<PlayerRatingAggregate> aggregateByGameId(@Param("liveGameId") String liveGameId);

	@Query("""
			SELECT r.rating AS rating,
			       COUNT(r.id) AS ratingCount
			FROM LivePlayerRating r
			WHERE r.liveGameId = :liveGameId
			  AND r.liveParticipantId = :liveParticipantId
			GROUP BY r.rating
			""")
	List<RatingDistributionAggregate> distribution(
			@Param("liveGameId") String liveGameId,
			@Param("liveParticipantId") Integer liveParticipantId);

	interface PlayerRatingAggregate {
		Integer getParticipantId();
		Double getAverageRating();
		Long getRatingCount();
	}

	interface RatingDistributionAggregate {
		Integer getRating();
		Long getRatingCount();
	}
}
