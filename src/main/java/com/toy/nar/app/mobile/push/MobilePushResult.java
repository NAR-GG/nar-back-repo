package com.toy.nar.app.mobile.push;

import java.util.List;

public record MobilePushResult(
		int successCount,
		int failureCount,
		List<String> invalidTokens) {
}
