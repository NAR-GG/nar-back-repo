package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberFavoritePlayer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MemberFavoritePlayerRepository extends JpaRepository<MemberFavoritePlayer, Long> {

	@EntityGraph(attributePaths = "player")
	List<MemberFavoritePlayer> findAllByMember_Id(Long memberId);

	@EntityGraph(attributePaths = "player")
	Optional<MemberFavoritePlayer> findByMember_IdAndPlayer_Id(Long memberId, Long playerId);

	@Query("""
			SELECT favorite.player.id
			FROM MemberFavoritePlayer favorite
			WHERE favorite.member.id = :memberId
			""")
	Set<Long> findPlayerIdsByMemberId(@Param("memberId") Long memberId);
}
