package com.toy.nar.app.mobile.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FirebaseMobilePushGateway implements MobilePushGateway {

	private static final int MAX_MULTICAST_TOKENS = 500;

	/** Android 무음 발송용 중요도 낮은 채널. 플러터가 같은 id 로 채널을 만든다. */
	private static final String QUIET_CHANNEL_ID = "warding_quiet";

	private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

	@Override
	public boolean isAvailable() {
		return firebaseMessagingProvider.getIfAvailable() != null;
	}

	@Override
	public MobilePushResult send(List<String> tokens, MobilePushMessage message) {
		FirebaseMessaging firebaseMessaging = firebaseMessagingProvider.getIfAvailable();
		if (firebaseMessaging == null) {
			throw new IllegalStateException("FCM 발송이 비활성화되어 있습니다.");
		}

		int successCount = 0;
		int failureCount = 0;
		List<String> invalidTokens = new ArrayList<>();
		List<String> successTokens = new ArrayList<>();
		for (int start = 0; start < tokens.size(); start += MAX_MULTICAST_TOKENS) {
			List<String> batchTokens = tokens.subList(
					start,
					Math.min(start + MAX_MULTICAST_TOKENS, tokens.size()));
			BatchResponse response = sendBatch(firebaseMessaging, batchTokens, message);
			successCount += response.getSuccessCount();
			failureCount += response.getFailureCount();
			collectInvalidTokens(batchTokens, response.getResponses(), invalidTokens);
			collectSuccessTokens(batchTokens, response.getResponses(), successTokens);
		}
		return new MobilePushResult(
				successCount, failureCount, List.copyOf(invalidTokens), List.copyOf(successTokens));
	}

	private BatchResponse sendBatch(
			FirebaseMessaging firebaseMessaging,
			List<String> tokens,
			MobilePushMessage message) {
		MulticastMessage multicastMessage = MulticastMessage.builder()
				.setNotification(Notification.builder()
						.setTitle(message.title())
						.setBody(message.body())
						.build())
				.putAllData(message.data())
				.setAndroidConfig(androidConfig(message))
				.setApnsConfig(apnsConfig(message))
				.addAllTokens(tokens)
				.build();
		try {
			return firebaseMessaging.sendEachForMulticast(multicastMessage);
		} catch (FirebaseMessagingException e) {
			throw new IllegalStateException("FCM 멀티캐스트 발송에 실패했습니다.", e);
		}
	}

	/**
	 * Android O+ 는 채널 설정이 payload 보다 우선한다 — priority 만 낮춰선 소리가 그대로 난다.
	 * 그래서 무음은 중요도 낮은 채널을 따로 지정해야 한다. 채널은 앱이 만든다.
	 */
	AndroidConfig androidConfig(MobilePushMessage message) {
		AndroidConfig.Builder builder = AndroidConfig.builder()
				.setPriority(message.silent() ? AndroidConfig.Priority.NORMAL : AndroidConfig.Priority.HIGH);
		if (message.silent()) {
			builder.setNotification(AndroidNotification.builder()
					.setChannelId(QUIET_CHANNEL_ID)
					.build());
		}
		return builder.build();
	}

	/** iOS 무음은 서버만으로 된다 — sound 를 비우고 passive 로 보내면 배너 없이 알림함에만 남는다. */
	ApnsConfig apnsConfig(MobilePushMessage message) {
		Aps.Builder aps = Aps.builder();
		if (message.silent()) {
			aps.putCustomData("interruption-level", "passive");
		} else {
			aps.setSound("default");
		}
		return ApnsConfig.builder().setAps(aps.build()).build();
	}

	/** 여러 구독자의 토큰을 한 번에 보낼 때, 누가 받았는지 되돌리려면 성공 토큰이 필요하다. */
	private void collectSuccessTokens(
			List<String> tokens,
			List<SendResponse> responses,
			List<String> successTokens) {
		for (int index = 0; index < responses.size(); index++) {
			if (responses.get(index).isSuccessful()) {
				successTokens.add(tokens.get(index));
			}
		}
	}

	private void collectInvalidTokens(
			List<String> tokens,
			List<SendResponse> responses,
			List<String> invalidTokens) {
		for (int index = 0; index < responses.size(); index++) {
			SendResponse response = responses.get(index);
			if (response.isSuccessful() || response.getException() == null) {
				continue;
			}
			MessagingErrorCode errorCode = response.getException().getMessagingErrorCode();
			if (errorCode == MessagingErrorCode.UNREGISTERED
					|| errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
				invalidTokens.add(tokens.get(index));
			}
		}
	}
}
