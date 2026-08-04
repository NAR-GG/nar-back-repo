package com.toy.nar.app.mobile.push;

import com.toy.nar.app.mobile.notification.MemberNotificationService;
import com.toy.nar.common.util.KoreanParticle;
import com.toy.nar.domain.member.entity.MemberDevice;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;
import com.toy.nar.domain.member.repository.PlayerSoloRankPushDeliveryRepository;
import com.toy.nar.domain.participant.entity.Player;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerSoloRankPushService {

	private static final String PUSH_TYPE = "PLAYER_SOLO_RANK_STARTED";

	/** '전체 선수 솔랭 알림'(앱에서 구독하는 FCM 토픽) — 모바일과 이름이 일치해야 한다. */
	private static final String ALL_SOLO_RANK_TOPIC = "all_solo_rank";

	private final MemberDeviceRepository deviceRepository;
	private final PlayerSoloRankPushDeliveryRepository deliveryRepository;
	private final MobilePushGateway pushGateway;
	private final MemberNotificationService notificationService;

	public void notifySubscribers(
			Player player,
			String gameId,
			String championName,
			String championImageUrl,
			String queueDisplayName,
			String opggUrl) {
		if (player == null || player.getId() == null || gameId == null || !pushGateway.isAvailable()) {
			return;
		}
		String normalizedChampion = normalizeChampionName(championName);
		MobilePushMessage message = buildMessage(
				player, gameId, championName, championImageUrl, queueDisplayName, opggUrl,
				player.getName() + " 선수가 솔랭을 시작했어요",
				normalizedChampion + KoreanParticle.ro(normalizedChampion) + " "
						+ normalizeQueue(queueDisplayName) + " 플레이 중");
		dispatch(player, gameId, message);
	}

	/**
	 * 경기 종료 후 폴백 알림(match-v5 감지). 스트리머 모드 계정은 라이브 감지가 불가능해
	 * 사후에라도 발송한다. 문구만 다르고 발송 파이프라인·중복 방지는 라이브 알림과 동일.
	 */
	public void notifySubscribersPostGame(
			Player player,
			String gameId,
			String championName,
			String championImageUrl,
			String resultLine,
			String opggUrl) {
		if (player == null || player.getId() == null || gameId == null || !pushGateway.isAvailable()) {
			return;
		}
		MobilePushMessage message = buildMessage(
				player, gameId, championName, championImageUrl, "솔로 랭크", opggUrl,
				player.getName() + " 선수가 솔랭 한 판을 마쳤어요",
				resultLine);
		dispatch(player, gameId, message);
	}

	private void dispatch(Player player, String gameId, MobilePushMessage message) {

		// 전체 선수 솔랭 알림 토픽 구독자에게 발송 (구독 여부와 무관). 게임당 1회.
		sendToAllSoloRankTopic(player, gameId, message);

		try {
			fanOutBatched(
					deviceRepository.findActiveDevicesBySubscribedPlayerId(player.getId()),
					player,
					gameId,
					message);
		} catch (Exception e) {
			log.warn(
					"Failed to prepare player solo rank pushes playerId={} gameId={}",
					player.getId(),
					gameId,
					e);
		}
	}

	private void sendToAllSoloRankTopic(Player player, String gameId, MobilePushMessage message) {
		try {
			pushGateway.sendToTopic(ALL_SOLO_RANK_TOPIC, message);
		} catch (Exception e) {
			log.warn(
					"Failed to send all-solo-rank topic push playerId={} gameId={}",
					player.getId(),
					gameId,
					e);
		}
	}

	/**
	 * 구독자 전원에게 한 번의 발송 호출로 보낸다.
	 *
	 * <p>예전엔 구독자마다 {@code pushGateway.send} 와 예약·마감 쿼리를 한 건씩 돌았다.
	 * 실측 2026-08-04 프로덕션: Oner 구독자 1,502명 팬아웃이 472초(1명당 0.31초)였고,
	 * 이 팬아웃이 솔랭 폴 스레드에서 동기로 돌기 때문에 그 사이 추적 100계정 폴링이 통째로 멈췄다
	 * (22:27→22:41 동안 신규 게임 감지 0건, 알림이 게임 시작 10분 뒤에 도착).</p>
	 *
	 * <p>게이트웨이가 500토큰씩 멀티캐스트하므로 토큰을 모아 한 번에 넘기면 FCM 왕복이
	 * 구독자 수에서 500토큰 단위로 줄고, 예약·마감도 벌크라 DB 왕복이 상수가 된다.
	 * 발송 후 토큰별 성공 여부로 구독자를 되돌려 기록한다.</p>
	 */
	private void fanOutBatched(
			List<MemberDevice> devices,
			Player player,
			String gameId,
			MobilePushMessage message) {
		Map<Long, List<MemberDevice>> devicesByMember = devices.stream()
				.collect(Collectors.groupingBy(
						device -> device.getMember().getId(),
						LinkedHashMap::new,
						Collectors.toList()));
		if (devicesByMember.isEmpty()) {
			return;
		}

		// dedup: (member, playerId, gameId) 당 1회만 통과한다. 구독자 수만큼 왕복하지 않도록
		// 한 번에 예약하고 발송 대상만 돌려받는다.
		List<Long> reservedIds = deliveryRepository.reserveAll(
				devicesByMember.keySet(), player.getId(), gameId);
		if (reservedIds.isEmpty()) {
			return;
		}

		Map<Long, List<String>> tokensByMember = new LinkedHashMap<>();
		for (Long memberId : reservedIds) {
			List<MemberDevice> memberDevices = devicesByMember.get(memberId);
			if (memberDevices == null) {
				continue;
			}
			tokensByMember.put(memberId, memberDevices.stream().map(MemberDevice::getFcmToken).toList());
		}
		if (tokensByMember.isEmpty()) {
			return;
		}

		List<String> allTokens = tokensByMember.values().stream().flatMap(List::stream).toList();
		MobilePushResult result;
		try {
			result = pushGateway.send(allTokens, message);
		} catch (Exception e) {
			// 발송 자체가 실패하면 예약한 구독자 전원을 FAILED 로 남긴다(재예약 대상이 된다).
			markFailedAll(List.copyOf(tokensByMember.keySet()), player.getId(), gameId, truncate(e.getMessage()));
			log.warn(
					"Player solo rank multicast failed playerId={} gameId={} members={} tokens={}",
					player.getId(),
					gameId,
					tokensByMember.size(),
					allTokens.size(),
					e);
			return;
		}

		Set<String> successTokens = new HashSet<>(result.successTokens());
		List<Long> delivered = new ArrayList<>();
		List<Long> undelivered = new ArrayList<>();
		tokensByMember.forEach((memberId, tokens) ->
				(tokens.stream().anyMatch(successTokens::contains) ? delivered : undelivered).add(memberId));

		markSentAll(delivered, player.getId(), gameId);
		markFailedAll(undelivered, player.getId(), gameId, "FCM 전송 성공 기기가 없습니다.");
		recordFeedAll(delivered, message);

		if (!result.invalidTokens().isEmpty()) {
			deactivateInvalidTokens(result.invalidTokens(), player.getId(), gameId);
		}
	}

	private void deactivateInvalidTokens(
			List<String> invalidTokens,
			Long playerId,
			String gameId) {
		try {
			deviceRepository.deactivateByFcmTokenIn(invalidTokens);
		} catch (Exception e) {
			log.warn(
					"Failed to deactivate invalid FCM tokens playerId={} gameId={}",
					playerId,
					gameId,
					e);
		}
	}

	private void markSentAll(Collection<Long> memberIds, Long playerId, String gameId) {
		if (memberIds.isEmpty()) {
			return;
		}
		try {
			deliveryRepository.markSentAll(memberIds, playerId, gameId);
		} catch (Exception e) {
			log.warn(
					"Failed to persist player push success playerId={} gameId={} members={}",
					playerId,
					gameId,
					memberIds.size(),
					e);
		}
	}

	private void markFailedAll(
			Collection<Long> memberIds,
			Long playerId,
			String gameId,
			String errorMessage) {
		if (memberIds.isEmpty()) {
			return;
		}
		try {
			deliveryRepository.markFailedAll(memberIds, playerId, gameId, errorMessage);
		} catch (Exception persistenceException) {
			log.warn(
					"Failed to persist player push failure playerId={} gameId={} members={}",
					playerId,
					gameId,
					memberIds.size(),
					persistenceException);
		}
	}

	/** 마이구독 알림 피드에 기록한다. 피드 실패가 푸시 흐름을 깨면 안 되므로 예외를 흡수한다. */
	private void recordFeedAll(Collection<Long> memberIds, MobilePushMessage message) {
		if (memberIds.isEmpty()) {
			return;
		}
		try {
			notificationService.recordAll(
					memberIds,
					MemberNotificationType.PLAYER_SOLO_RANK_STARTED,
					message.title(),
					message.body(),
					message.data());
		} catch (Exception e) {
			log.warn("Failed to record solo rank notification feed members={}", memberIds.size(), e);
		}
	}

	private MobilePushMessage buildMessage(
			Player player,
			String gameId,
			String championName,
			String championImageUrl,
			String queueDisplayName,
			String opggUrl,
			String title,
			String body) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("type", PUSH_TYPE);
		data.put("playerId", String.valueOf(player.getId()));
		data.put("playerName", player.getName());
		data.put("gameId", gameId);
		data.put("championName", normalizeChampionName(championName));
		data.put("queueType", normalizeQueue(queueDisplayName));
		data.put("deepLink", "nar://players/" + player.getId());
		if (championImageUrl != null && !championImageUrl.isBlank()) {
			data.put("championImageUrl", championImageUrl);
		}
		if (opggUrl != null && !opggUrl.isBlank()) {
			data.put("opggUrl", opggUrl);
		}
		return new MobilePushMessage(title, body, Map.copyOf(data));
	}

	private String normalizeChampionName(String championName) {
		return championName == null || championName.isBlank() ? "챔피언 정보 확인 중" : championName;
	}

	private String normalizeQueue(String queueDisplayName) {
		return queueDisplayName == null || queueDisplayName.isBlank() ? "솔로 랭크" : queueDisplayName;
	}

	private String truncate(String message) {
		String normalized = message == null || message.isBlank() ? "알 수 없는 FCM 오류" : message;
		return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
	}
}
