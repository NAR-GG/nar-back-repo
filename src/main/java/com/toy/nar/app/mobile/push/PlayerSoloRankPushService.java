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

	/** 발송 시점 구분. 멱등 키와 구독 토글 조회에 함께 쓰인다. */
	public static final String EVENT_START = "START";
	public static final String EVENT_END = "END";


	private static final String PUSH_TYPE = "PLAYER_SOLO_RANK_STARTED";

	private final MemberDeviceRepository deviceRepository;
	private final PlayerSoloRankPushDeliveryRepository deliveryRepository;
	private final MobilePushGateway pushGateway;
	private final MemberNotificationService notificationService;
	private final QuietAwarePushSender quietAwarePushSender;

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
						+ normalizeQueue(queueDisplayName) + " 플레이 중",
				Map.of("eventType", EVENT_START));
		dispatch(player, gameId, EVENT_START, message);
	}

	/**
	 * 경기 종료 알림(전이 감지 + 스트리머 모드 계정용 match-v5 폴백). 문구만 다르고 발송
	 * 파이프라인·중복 방지는 시작 알림과 동일하다.
	 *
	 * <p>{@code win}·{@code kda}·{@code gameDurationSeconds} 는 앱이 문구를 직접 조립할 수
	 * 있도록 data 에 따로 싣는다. 초 단위로 넣는 건 앱이 "28분"으로 줄일지 "28:14"로 쓸지를
	 * 나중에 고를 수 있게 하려는 것이다 — 문구를 바꾸려고 서버를 배포하지 않아도 된다.
	 * 앱은 솔랭 카드만 서버 title/body 를 쓰지 않고 로케일에 맞춰 재조립하는데, data 에
	 * 시작/종료 구분이 없어 종료 알림이 시작 문구로 그려지고 있었다.</p>
	 */
	public void notifySubscribersPostGame(
			Player player,
			String gameId,
			String championName,
			String championImageUrl,
			String resultLine,
			Boolean win,
			String kda,
			Integer gameDurationSeconds,
			String opggUrl) {
		if (player == null || player.getId() == null || gameId == null || !pushGateway.isAvailable()) {
			return;
		}
		Map<String, String> endData = new LinkedHashMap<>();
		endData.put("eventType", EVENT_END);
		if (win != null) {
			endData.put("win", String.valueOf(win));
		}
		if (kda != null && !kda.isBlank()) {
			endData.put("kda", kda);
		}
		if (gameDurationSeconds != null && gameDurationSeconds > 0) {
			endData.put("gameDurationSeconds", String.valueOf(gameDurationSeconds));
		}
		MobilePushMessage message = buildMessage(
				player, gameId, championName, championImageUrl, "솔로 랭크", opggUrl,
				player.getName() + " 선수가 솔랭을 끝냈어요",
				resultLine,
				endData);
		dispatch(player, gameId, EVENT_END, message);
	}

	private void dispatch(Player player, String gameId, String eventType, MobilePushMessage message) {
		try {
			fanOutBatched(
					deviceRepository.findActiveDevicesBySubscribedPlayerId(player.getId(), eventType),
					player,
					gameId,
					eventType,
					message);
		} catch (Exception e) {
			log.warn(
					"Failed to prepare player solo rank pushes playerId={} gameId={}",
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
			String eventType,
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
				devicesByMember.keySet(), player.getId(), gameId, eventType);
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

		// 알림함(마이구독 피드)은 발송 '전에' 남긴다. 예전엔 멀티캐스트와 마감 뒤에 기록해서,
		// 푸시를 받고 바로 마이구독을 열면 아직 행이 없었다 — 2026-08-11 프로덕션 실측
		// (Zeus 구독 1,440명): 예약 00:12:23 → 발송 완료 00:12:33 → 피드 INSERT 00:12:53 로
		// 기기 수신부터 약 29초. 그 화면은 진입·복귀 때만 다시 읽기 때문에 한 번 비면
		// 나갔다 들어오기 전까지 계속 비어 있었다. 구독자가 많은 선수일수록 확실히 터진다.
		//
		// ponytail: 발송이 실패한 회원도 피드에 남는다(실측 6,181건 중 FAILED 1건). 재예약된
		// 재시도라면 같은 알림이 두 줄 남을 수 있는데, 그 빈도가 위 지연보다 훨씬 낮아
		// dedup 장치는 두지 않는다. 필요해지면 (member, playerId, gameId) 유니크로 막는다.
		recordFeedAll(tokensByMember.keySet(), message);

		List<String> allTokens = tokensByMember.values().stream().flatMap(List::stream).toList();
		QuietAwarePushSender.Outcome outcome;
		try {
			// 잠자기 회원은 발송에서 빠지고 알림함에만 남는다. 나머지는 한 번에 멀티캐스트.
			outcome = quietAwarePushSender.send(tokensByMember, message);
		} catch (Exception e) {
			// 발송 자체가 실패하면 예약한 구독자 전원을 FAILED 로 남긴다(재예약 대상이 된다).
			markFailedAll(List.copyOf(tokensByMember.keySet()), player.getId(), gameId, eventType,
					truncate(e.getMessage()));
			log.warn(
					"Player solo rank multicast failed playerId={} gameId={} members={} tokens={}",
					player.getId(),
					gameId,
					tokensByMember.size(),
					allTokens.size(),
					e);
			return;
		}

		MobilePushResult result = outcome.result();
		Set<String> successTokens = new HashSet<>(result.successTokens());
		List<Long> delivered = new ArrayList<>();
		List<Long> undelivered = new ArrayList<>();
		tokensByMember.forEach((memberId, tokens) -> {
			// 잠자기로 건너뛴 회원은 발송 성공/실패 어느 쪽도 아니다.
			if (outcome.isSkipped(memberId)) {
				return;
			}
			(tokens.stream().anyMatch(successTokens::contains) ? delivered : undelivered).add(memberId);
		});

		markSentAll(delivered, player.getId(), gameId, eventType);
		// 잠자기로 건너뛴 회원은 발송 없이 마감만 한다 — 알림함에는 위에서 이미 남겼다.
		markSkippedQuietAll(outcome.skippedMemberIds(), player.getId(), gameId, eventType);
		markFailedAll(undelivered, player.getId(), gameId, eventType, "FCM 전송 성공 기기가 없습니다.");

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

	private void markSentAll(Collection<Long> memberIds, Long playerId, String gameId, String eventType) {
		if (memberIds.isEmpty()) {
			return;
		}
		try {
			deliveryRepository.markSentAll(memberIds, playerId, gameId, eventType);
		} catch (Exception e) {
			log.warn(
					"Failed to persist player push success playerId={} gameId={} members={}",
					playerId,
					gameId,
					memberIds.size(),
					e);
		}
	}

	private void markSkippedQuietAll(Collection<Long> memberIds, Long playerId, String gameId, String eventType) {
		if (memberIds.isEmpty()) {
			return;
		}
		try {
			deliveryRepository.markSkippedQuietAll(memberIds, playerId, gameId, eventType);
		} catch (Exception e) {
			log.warn(
					"Failed to persist quiet-skipped delivery playerId={} gameId={} members={}",
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
			String eventType,
			String errorMessage) {
		if (memberIds.isEmpty()) {
			return;
		}
		try {
			deliveryRepository.markFailedAll(memberIds, playerId, gameId, eventType, errorMessage);
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
			String body,
			Map<String, String> extra) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("type", PUSH_TYPE);
		// data.type 은 앱의 딥링크 라우팅 키라 시작/종료가 같은 값을 써야 한다.
		// 시작/종료 구분은 eventType 으로 따로 싣는다.
		data.putAll(extra);
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
