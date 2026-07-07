package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberMatchSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberMatchSubscriptionRepository
		extends JpaRepository<MemberMatchSubscription, Long> {

	boolean existsByMemberIdAndMatchId(Long memberId, String matchId);

	void deleteByMemberIdAndMatchId(Long memberId, String matchId);

	@Query("""
			SELECT subscription.matchId
			FROM MemberMatchSubscription subscription
			WHERE subscription.member.id = :memberId
			""")
	List<String> findMatchIdsByMemberId(@Param("memberId") Long memberId);
}
