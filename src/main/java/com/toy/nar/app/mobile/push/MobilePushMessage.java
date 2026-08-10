package com.toy.nar.app.mobile.push;

import java.util.Map;

/**
 * 푸시 한 건.
 *
 * @param silent 알림 잠자기 시간대 발송. 소리·배너 없이 알림함에만 쌓이게 보낸다.
 */
public record MobilePushMessage(
		String title,
		String body,
		Map<String, String> data,
		boolean silent) {

	/** 기존 호출처를 위한 소리 있는 발송. */
	public MobilePushMessage(String title, String body, Map<String, String> data) {
		this(title, body, data, false);
	}

	public MobilePushMessage asSilent() {
		return new MobilePushMessage(title, body, data, true);
	}
}
