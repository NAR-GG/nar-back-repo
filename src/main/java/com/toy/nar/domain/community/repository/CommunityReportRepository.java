package com.toy.nar.domain.community.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.toy.nar.domain.community.entity.CommunityReport;

public interface CommunityReportRepository extends JpaRepository<CommunityReport, Long> {

	/** uk (target_type, target_id, reporter_id) prefix 로 도는 카운트. */
	@Query("""
			SELECT COUNT(r) FROM CommunityReport r
			WHERE r.targetType = :targetType AND r.targetId = :targetId AND r.status = 'PENDING'
			""")
	long countPending(CommunityReport.TargetType targetType, Long targetId);

	/** Discord 알림에 싣는 사유 분포. [reason, count] 쌍. */
	@Query("""
			SELECT r.reason, COUNT(r) FROM CommunityReport r
			WHERE r.targetType = :targetType AND r.targetId = :targetId AND r.status = 'PENDING'
			GROUP BY r.reason
			""")
	List<Object[]> countPendingByReason(CommunityReport.TargetType targetType, Long targetId);
}
