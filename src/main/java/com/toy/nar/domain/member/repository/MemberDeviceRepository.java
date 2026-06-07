package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberDevice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberDeviceRepository extends JpaRepository<MemberDevice, Long> {

	Optional<MemberDevice> findByFcmToken(String fcmToken);

	Optional<MemberDevice> findByIdAndMember_Id(Long id, Long memberId);

	@EntityGraph(attributePaths = "member")
	@Query("""
			SELECT DISTINCT device
			FROM MemberDevice device
			WHERE device.active = true
			  AND EXISTS (
				  SELECT favorite.id
				  FROM MemberFavoritePlayer favorite
				  WHERE favorite.member = device.member
				    AND favorite.player.id = :playerId
			  )
			""")
	List<MemberDevice> findActiveDevicesBySubscribedPlayerId(@Param("playerId") Long playerId);

	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("""
			UPDATE MemberDevice device
			SET device.active = false
			WHERE device.fcmToken IN :tokens
			""")
	int deactivateByFcmTokenIn(@Param("tokens") Collection<String> tokens);
}
