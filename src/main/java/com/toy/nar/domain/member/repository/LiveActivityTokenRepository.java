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

	/**
	 * 매치가 끝나 그 경기의 카드를 전부 정리한다.
	 *
	 * <p>토큰 목록을 IN 절로 넘기지 않는다. 카드가 많은 경기면 IN 절에 토큰 수만큼 문자열이
	 * 실려(1,500장이면 100KB 넘는 SQL) 파싱·플래닝이 목록 크기를 따라 커진다. 매치 종료는
	 * "이 매치 전부"라 조건을 그대로 쓰면 되고, 그러면 (match_id, active) 인덱스 한 번으로 끝난다.</p>
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update LiveActivityToken t set t.active = false, t.updatedAt = CURRENT_TIMESTAMP "
			+ "where t.matchId = :matchId and t.active = true")
	int deactivateAllByMatchId(@Param("matchId") String matchId);
}
