package com.toy.nar.app.mobile.push;

import com.google.firebase.messaging.AndroidConfig;
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
				.setAndroidConfig(androidConfig())
				.setApnsConfig(apnsConfig())
				.addAllTokens(tokens)
				.build();
		try {
			return firebaseMessaging.sendEachForMulticast(multicastMessage);
		} catch (FirebaseMessagingException e) {
			throw new IllegalStateException("FCM 멀티캐스트 발송에 실패했습니다.", e);
		}
	}

	/**
	 * 알림 잠자기는 무음 발송이 아니라 <b>발송 자체를 건너뛰는</b> 방식이라
	 * (QuietAwarePushSender 참고) 게이트웨이는 소리 나는 발송만 만든다.
	 */
	AndroidConfig androidConfig() {
		return AndroidConfig.builder()
				.setPriority(AndroidConfig.Priority.HIGH)
				.build();
	}

	ApnsConfig apnsConfig() {
		return ApnsConfig.builder()
				.setAps(Aps.builder().setSound("default").build())
				.build();
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
