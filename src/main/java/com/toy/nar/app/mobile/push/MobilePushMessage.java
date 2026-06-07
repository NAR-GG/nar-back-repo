package com.toy.nar.app.mobile.push;

import java.util.Map;

public record MobilePushMessage(
		String title,
		String body,
		Map<String, String> data) {
}
