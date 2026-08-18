package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberFavoritePlayer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

	/**
	 * 구독을 추가한다. 이미 있으면 아무것도 하지 않는다 — 중복 판정을 유니크 제약
	 * {@code uq_member_favorite_player} 에 맡긴다. 스스로 트랜잭션을 열어 한 문장으로 끝난다.
	 *
	 * <p>"SELECT 로 있는지 보고 없으면 save" 는 동시 요청에서 깨진다. SELECT 는 락을 잡지 않아
	 * 같은 (member, player) 를 노리는 트랜잭션들이 전부 "없음" 으로 보고 다 같이 INSERT 하고,
	 * 유니크 인덱스에서 duplicate(1062) 가 터졌다. 자세한 경위는
	 * {@code MobilePlayerSubscriptionService#subscribe} 주석 참고.</p>
	 *
	 * <p>{@code INSERT IGNORE} 가 아니라 {@code ON DUPLICATE KEY UPDATE} 인 이유: IGNORE 는
	 * 중복만이 아니라 FK 위반(1452)까지 warning 으로 강등해 조용히 0행을 넣는다. 회원이 동시에
	 * 탈퇴하면 행이 안 들어갔는데도 "구독됨" 을 반환하게 된다. ODKU 는 중복만 삼키고 FK 위반은
	 * 그대로 예외로 올린다(실측, {@code MemberFavoritePlayerUpsertMySqlIntegrationTest}).</p>
	 *
	 * <p>{@code UPDATE id = id} 는 아무것도 바꾸지 않겠다는 뜻이다 — 재호출이 기존
	 * {@code created_at} 을 덮으면 "언제부터 구독했나" 가 매 요청마다 리셋된다.</p>
	 *
	 * <p>반환값을 두지 않는다. MySQL JDBC 는 기본이 affected-rows 가 아니라 found-rows 라
	 * 중복이라 아무것도 안 했을 때도 1 을 준다(실측). "새로 넣었는지" 를 이 값으로 판별하면 틀린다.</p>
	 */
	@Modifying
	@Transactional
	@Query(value = """
			INSERT INTO member_favorite_player
			       (member_id, player_id, start_enabled, end_enabled, created_at)
			VALUES (:memberId, :playerId, TRUE, FALSE, :createdAt)
			ON DUPLICATE KEY UPDATE id = id
			""", nativeQuery = true)
	void insertIfAbsent(
			@Param("memberId") Long memberId,
			@Param("playerId") Long playerId,
			@Param("createdAt") LocalDateTime createdAt);

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
