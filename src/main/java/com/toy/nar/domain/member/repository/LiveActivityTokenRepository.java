package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.LiveActivityToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface LiveActivityTokenRepository extends JpaRepository<LiveActivityToken, Long> {

	Optional<LiveActivityToken> findByPushToken(String pushToken);

	@Query("select t.pushToken from LiveActivityToken t where t.matchId = :matchId and t.active = true")
	List<String> findActivePushTokensByMatchId(@Param("matchId") String matchId);

	/**
	 * APNs 가 410(Unregistered) 등으로 거절한 토큰은 더 쓰지 않는다.
	 *
	 * <p>호출측(LiveActivityPushService)이 APNs 왕복 뒤에 부르므로 트랜잭션을 여기 둔다 —
	 * 서비스 전체를 트랜잭션으로 감싸면 네트워크 I/O 동안 커넥션을 붙들게 된다.</p>
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update LiveActivityToken t set t.active = false, t.updatedAt = CURRENT_TIMESTAMP "
			+ "where t.pushToken in :pushTokens")
	int deactivateByPushTokenIn(@Param("pushTokens") List<String> pushTokens);
}
