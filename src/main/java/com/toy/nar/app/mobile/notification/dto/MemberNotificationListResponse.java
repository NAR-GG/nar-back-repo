package com.toy.nar.app.mobile.notification.dto;

import com.toy.nar.domain.member.entity.MemberNotification;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "마이구독 알림 리스트 전체 페이지 응답")
public record MemberNotificationListResponse(
		List<Item> notifications,
		@Schema(description = "미읽음 알림 수(필터와 무관한 전체 기준)", example = "3")
		long unreadCount,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public record Item(
			Long id,
			@Schema(description = "알림 종류", example = "SET_END")
			MemberNotificationType type,
			String title,
			String body,
			@Schema(description = "딥링크·참조 식별자(playerId/matchId/gameId/setNumber 등)")
			Map<String, String> data,
			@Schema(description = "읽음 여부", example = "false")
			boolean read,
			LocalDateTime createdAt) {

		public static Item from(MemberNotification n) {
			return new Item(
					n.getId(),
					n.getType(),
					n.getTitle(),
					n.getBody(),
					n.getData(),
					n.isRead(),
					n.getCreatedAt());
		}
	}
}
