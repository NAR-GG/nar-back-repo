package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberNotification;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MemberNotificationRepository extends JpaRepository<MemberNotification, Long> {

	Page<MemberNotification> findByMember_IdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

	Page<MemberNotification> findByMember_IdAndTypeOrderByCreatedAtDesc(
			Long memberId, MemberNotificationType type, Pageable pageable);

	long countByMember_IdAndReadAtIsNull(Long memberId);

	Optional<MemberNotification> findByIdAndMember_Id(Long id, Long memberId);

	@Modifying(clearAutomatically = true)
	@Query("UPDATE MemberNotification n SET n.readAt = :now WHERE n.member.id = :memberId AND n.readAt IS NULL")
	int markAllReadByMember(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
