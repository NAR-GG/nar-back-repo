package com.toy.nar.app.community.service;

import java.util.List;

import com.toy.nar.domain.community.entity.CommunityReport.TargetType;

/** 신고 임계 도달. reasonRows 는 [Reason, count] 쌍 목록. */
public record CommunityReportAlertEvent(
		TargetType targetType,
		long targetId,
		long pendingCount,
		List<Object[]> reasonRows,
		String preview) {
}
