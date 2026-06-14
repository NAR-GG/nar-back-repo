package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.entity.MemberDevice;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;
import com.toy.nar.domain.member.repository.PlayerSoloRankPushDeliveryRepository;
import com.toy.nar.domain.participant.entity.Player;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	public void notifySubscribers(
			Player player,
			String gameId,
			String championName,
			String championImageUrl) {
		if (player == null || player.getId() == null || gameId == null || !pushGateway.isAvailable()) {
			return;
		}

		MobilePushMessage message = buildMessage(player, gameId, championName, championImageUrl);

		// 전체 선수 솔랭 알림 토픽 구독자에게 발송 (구독 여부와 무관). 게임당 1회.
		sendToAllSoloRankTopic(player, gameId, message);

		try {
			List<MemberDevice> devices = deviceRepository.findActiveDevicesBySubscribedPlayerId(player.getId());
			Map<Long, List<MemberDevice>> devicesByMember = devices.stream()
					.collect(Collectors.groupingBy(
							device -> device.getMember().getId(),
							LinkedHashMap::new,
							Collectors.toList()));

			for (Map.Entry<Long, List<MemberDevice>> entry : devicesByMember.entrySet()) {
				sendToMember(entry.getKey(), entry.getValue(), player, gameId, message);
			}
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

	private void sendToMember(
			Long memberId,
			List<MemberDevice> devices,
			Player player,
			String gameId,
			MobilePushMessage message) {
		try {
			if (deliveryRepository.reserve(memberId, player.getId(), gameId) == 0) {
				return;
			}
			List<String> tokens = devices.stream().map(MemberDevice::getFcmToken).toList();
			MobilePushResult result = pushGateway.send(tokens, message);
			if (!result.invalidTokens().isEmpty()) {
				deactivateInvalidTokens(result.invalidTokens(), player.getId(), gameId);
			}
			if (result.successCount() > 0) {
				deliveryRepository.markSent(memberId, player.getId(), gameId);
			} else {
				deliveryRepository.markFailed(
						memberId,
						player.getId(),
						gameId,
						"FCM 전송 성공 기기가 없습니다.");
			}
		} catch (Exception e) {
			String errorMessage = truncate(e.getMessage());
			markFailed(memberId, player.getId(), gameId, errorMessage);
			log.warn(
					"Player solo rank push failed memberId={} playerId={} gameId={}",
					memberId,
					player.getId(),
					gameId,
					e);
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

	private void markFailed(
			Long memberId,
			Long playerId,
			String gameId,
			String errorMessage) {
		try {
			deliveryRepository.markFailed(memberId, playerId, gameId, errorMessage);
		} catch (Exception persistenceException) {
			log.warn(
					"Failed to persist player push failure memberId={} playerId={} gameId={}",
					memberId,
					playerId,
					gameId,
					persistenceException);
		}
	}

	private MobilePushMessage buildMessage(
			Player player,
			String gameId,
			String championName,
			String championImageUrl) {
		String normalizedChampionName = championName == null || championName.isBlank()
				? "챔피언 정보 확인 중"
				: championName;
		Map<String, String> data = new LinkedHashMap<>();
		data.put("type", PUSH_TYPE);
		data.put("playerId", String.valueOf(player.getId()));
		data.put("gameId", gameId);
		data.put("championName", normalizedChampionName);
		data.put("deepLink", "nar://players/" + player.getId());
		if (championImageUrl != null && !championImageUrl.isBlank()) {
			data.put("championImageUrl", championImageUrl);
		}
		return new MobilePushMessage(
				player.getName() + " 선수가 솔랭을 시작했어요",
				normalizedChampionName + "로 솔로 랭크 플레이 중",
				Map.copyOf(data));
	}

	private String truncate(String message) {
		String normalized = message == null || message.isBlank() ? "알 수 없는 FCM 오류" : message;
		return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
	}
}
