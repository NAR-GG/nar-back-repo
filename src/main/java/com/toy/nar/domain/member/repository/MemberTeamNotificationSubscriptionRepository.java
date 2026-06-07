package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberTeamNotificationSubscription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberTeamNotificationSubscriptionRepository
		extends JpaRepository<MemberTeamNotificationSubscription, Long> {

	@EntityGraph(attributePaths = "team")
	List<MemberTeamNotificationSubscription> findByMember_Id(Long memberId);

	@EntityGraph(attributePaths = "team")
	Optional<MemberTeamNotificationSubscription> findByMember_IdAndTeam_Id(Long memberId, Long teamId);
}
