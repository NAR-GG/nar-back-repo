package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberFavoritePlayer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MemberFavoritePlayerRepository extends JpaRepository<MemberFavoritePlayer, Long> {

	@EntityGraph(attributePaths = "player")
	List<MemberFavoritePlayer> findAllByMember_Id(Long memberId);

	@EntityGraph(attributePaths = "player")
	Optional<MemberFavoritePlayer> findByMember_IdAndPlayer_Id(Long memberId, Long playerId);

	// 백오피스 회원 상세: 소속팀 이름까지 한 방에(선수마다 팀 조회하면 N+1).
	@EntityGraph(attributePaths = {"player", "player.currentTeam"})
	List<MemberFavoritePlayer> findWithPlayerAndTeamByMember_IdOrderByCreatedAtDesc(Long memberId);

	@Query("""
			SELECT favorite.player.id
			FROM MemberFavoritePlayer favorite
			WHERE favorite.member.id = :memberId
			""")
	Set<Long> findPlayerIdsByMemberId(@Param("memberId") Long memberId);

	// 백오피스 구독 탭: 특정 선수를 구독한 회원 목록(최근 구독순). 페이징.
	@Query(value = """
			SELECT m.id AS memberId,
			       m.name AS name,
			       m.tag AS tag,
			       m.email AS email,
			       mfp.created_at AS subscribedAt
			FROM member_favorite_player mfp
			JOIN member m ON m.id = mfp.member_id
			WHERE mfp.player_id = :playerId
			ORDER BY mfp.created_at DESC
			""",
			countQuery = "SELECT COUNT(*) FROM member_favorite_player WHERE player_id = :playerId",
			nativeQuery = true)
	Page<SubscriberView> findSubscribersByPlayerId(@Param("playerId") Long playerId, Pageable pageable);

	interface SubscriberView {
		Long getMemberId();

		String getName();

		String getTag();

		String getEmail();

		LocalDateTime getSubscribedAt();
	}
}
