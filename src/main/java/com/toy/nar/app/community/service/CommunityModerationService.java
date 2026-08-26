package com.toy.nar.app.community.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.community.dto.CommunityDtos.BlockCreateRequest;
import com.toy.nar.app.community.dto.CommunityDtos.ReportCreateRequest;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.community.entity.CommunityReport;
import com.toy.nar.domain.community.entity.CommunityReport.Reason;
import com.toy.nar.domain.community.entity.CommunityReport.TargetType;
import com.toy.nar.domain.community.repository.CommunityModerationRepository;
import com.toy.nar.domain.community.repository.CommunityReportRepository;
import com.toy.nar.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 신고·차단. 신고 임계(텍스트 3건 / 이미지 1건, D-3·D-7)에 도달하는 "순간" 한 번만
 * Discord 알림 이벤트를 낸다 — 발송은 AFTER_COMMIT 리스너가 트랜잭션 밖에서 한다
 * (락 수칙: 트랜잭션 안에 외부 HTTP 금지).
 */
@Service
@RequiredArgsConstructor
public class CommunityModerationService {

	private static final int TEXT_ALERT_THRESHOLD = 3;
	private static final int IMAGE_ALERT_THRESHOLD = 1;
	private static final int MAX_DETAIL_LENGTH = 200;

	private final CommunityReportRepository reportRepository;
	private final CommunityModerationRepository moderationRepository;
	private final MemberRepository memberRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public void report(Long reporterId, ReportCreateRequest request) {
		CommunityPostService.requireLogin(reporterId);
		TargetType targetType = parse(TargetType.class, request.targetType());
		Reason reason = parse(Reason.class, request.reason());
		String detail = normalizeDetail(reason, request.detail());
		if (request.targetId() == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		// 다형 참조라 FK 가 없다 — 실존·VISIBLE 검증이 여기 한 번뿐이다.
		String preview = moderationRepository.findVisibleTargetPreview(targetType, request.targetId())
				.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_REPORT_TARGET_NOT_FOUND));

		try {
			reportRepository.saveAndFlush(CommunityReport.builder()
					.targetType(targetType)
					.targetId(request.targetId())
					.reporterId(reporterId)
					.reason(reason)
					.detail(detail)
					.build());
		} catch (DataIntegrityViolationException e) {
			throw new CustomException(ErrorCode.COMMUNITY_ALREADY_REPORTED);
		}

		long pending = reportRepository.countPending(targetType, request.targetId());
		int threshold = targetType == TargetType.IMAGE ? IMAGE_ALERT_THRESHOLD : TEXT_ALERT_THRESHOLD;
		// 정확히 임계에 닿는 순간만 — 이후 매 신고마다 쏘면 같은 글로 알림이 도배된다.
		if (pending == threshold) {
			List<Object[]> reasonRows = reportRepository.countPendingByReason(targetType, request.targetId());
			eventPublisher.publishEvent(new CommunityReportAlertEvent(
					targetType, request.targetId(), pending, reasonRows, preview));
		}
	}

	public void block(Long memberId, BlockCreateRequest request) {
		CommunityPostService.requireLogin(memberId);
		Long targetId = request.memberId();
		if (targetId == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		if (targetId.equals(memberId)) {
			throw new CustomException(ErrorCode.COMMUNITY_BLOCK_SELF);
		}
		if (!memberRepository.existsById(targetId)) {
			throw new CustomException(ErrorCode.COMMUNITY_MEMBER_NOT_FOUND);
		}
		moderationRepository.insertBlock(memberId, targetId); // 중복 차단은 멱등
	}

	public void unblock(Long memberId, long blockedMemberId) {
		CommunityPostService.requireLogin(memberId);
		moderationRepository.deleteBlock(memberId, blockedMemberId); // 없어도 멱등
	}

	private static String normalizeDetail(Reason reason, String detail) {
		String trimmed = detail == null ? null : detail.trim();
		if (reason == Reason.ETC && (trimmed == null || trimmed.isEmpty())) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE); // ETC 는 사유 서술 필수
		}
		if (trimmed != null && trimmed.length() > MAX_DETAIL_LENGTH) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return trimmed == null || trimmed.isEmpty() ? null : trimmed;
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String value) {
		if (value == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException e) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
	}
}
