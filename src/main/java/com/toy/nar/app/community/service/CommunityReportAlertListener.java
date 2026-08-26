package com.toy.nar.app.community.service;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.domain.community.entity.CommunityReport.TargetType;

import lombok.RequiredArgsConstructor;

/**
 * 신고 임계 도달 → Discord 발송. AFTER_COMMIT(기본값)이라 신고 트랜잭션이 행 락을 쥔 채
 * 외부 HTTP 를 기다리는 일이 없고, 롤백된 신고로 알림이 나가지도 않는다.
 *
 * <p>백오피스가 아직 없어 알림에 처리 SQL 을 그대로 싣는다(D-7) — 알림 받고 쿼리를
 * 찾는 시간이 곧 노출 시간이다. 이미지는 @here 멘션으로 다른 알림과 구분한다.</p>
 */
@Component
@RequiredArgsConstructor
public class CommunityReportAlertListener {

	private final NotificationService notificationService;

	@TransactionalEventListener
	public void onThresholdReached(CommunityReportAlertEvent event) {
		String reasonSummary = event.reasonRows().stream()
				.map(row -> row[0] + " " + row[1] + "건")
				.collect(Collectors.joining(", "));

		String message = """
				대상: %s #%d
				누적 신고: %d건 (%s)
				내용: %s

				블라인드 처리 SQL:
				```sql
				%s
				```""".formatted(
				event.targetType(), event.targetId(), event.pendingCount(), reasonSummary,
				event.preview(), hideSql(event.targetType(), event.targetId()));

		boolean image = event.targetType() == TargetType.IMAGE;
		String title = image ? "[커뮤니티] 이미지 신고 — 즉시 확인" : "[커뮤니티] 신고 누적 " + event.pendingCount() + "건";
		notificationService.sendCommunityReportNotification(title, message, image);
	}

	private static String hideSql(TargetType targetType, long targetId) {
		String table = switch (targetType) {
			case POST -> "community_post";
			case COMMENT -> "community_comment";
			case IMAGE -> "community_post_image";
		};
		return "UPDATE " + table + " SET status = 'HIDDEN' WHERE id = " + targetId + ";";
	}
}
