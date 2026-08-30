package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberNotification;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface MemberNotificationRepository
		extends JpaRepository<MemberNotification, Long>, MemberNotificationRepositoryCustom {

	Page<MemberNotification> findByMember_IdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

	Page<MemberNotification> findByMember_IdAndTypeOrderByCreatedAtDesc(
			Long memberId, MemberNotificationType type, Pageable pageable);

	Page<MemberNotification> findByMember_IdAndTypeInOrderByCreatedAtDesc(
			Long memberId, Collection<MemberNotificationType> types, Pageable pageable);

	long countByMember_IdAndReadAtIsNull(Long memberId);

	long countByMember_IdAndTypeInAndReadAtIsNull(Long memberId, Collection<MemberNotificationType> types);

	Optional<MemberNotification> findByIdAndMember_Id(Long id, Long memberId);

	@Modifying(clearAutomatically = true)
	@Query("UPDATE MemberNotification n SET n.readAt = :now WHERE n.member.id = :memberId AND n.readAt IS NULL")
	int markAllReadByMember(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

	@Modifying(clearAutomatically = true)
	@Query("UPDATE MemberNotification n SET n.readAt = :now "
			+ "WHERE n.member.id = :memberId AND n.type IN :types AND n.readAt IS NULL")
	int markAllReadByMemberAndTypes(@Param("memberId") Long memberId,
			@Param("types") Collection<MemberNotificationType> types,
			@Param("now") LocalDateTime now);

	int deleteByIdAndMember_Id(Long id, Long memberId);

	@Modifying(clearAutomatically = true)
	@Query("DELETE FROM MemberNotification n WHERE n.member.id = :memberId")
	int deleteAllByMember(@Param("memberId") Long memberId);

	/**
	 * 보존 기간이 지난 알림을 타입별로 청크 삭제한다. 삭제한 행 수를 반환한다.
	 *
	 * <p>DB 서버가 RAM 1GB라 한 번에 지우면 언두로그·락이 버티지 못한다. JPQL 은 DELETE 에
	 * LIMIT 을 못 붙이므로 네이티브 쿼리를 쓰고, 메서드마다 트랜잭션을 열어 청크 단위로 커밋한다
	 * (호출부는 트랜잭션 밖이어야 한다).
	 */
	@Transactional
	@Modifying(clearAutomatically = true)
	@Query(value = "DELETE FROM member_notification WHERE type = :type AND created_at < :cutoff LIMIT :chunkSize",
			nativeQuery = true)
	int deleteOlderThanByType(@Param("type") String type,
			@Param("cutoff") LocalDateTime cutoff,
			@Param("chunkSize") int chunkSize);
}
