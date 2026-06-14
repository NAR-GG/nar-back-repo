package com.toy.nar.app.mobile.push;

import java.util.List;

public interface MobilePushGateway {

	boolean isAvailable();

	MobilePushResult send(List<String> tokens, MobilePushMessage message);

	/**
	 * 지정한 FCM 토픽 구독자 전체에게 발송한다(예: 전체 선수 솔랭 알림).
	 * 토큰 발송과 달리 구독자 집계 없이 토픽 하나로 보낸다.
	 */
	void sendToTopic(String topic, MobilePushMessage message);
}
