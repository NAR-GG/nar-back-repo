package com.toy.nar.app.mobile.push;

import java.util.List;

/**
 * FCM 발송 결과.
 *
 * @param successTokens 발송에 성공한 토큰. 여러 구독자의 토큰을 한 번에 보낼 때
 *                      "누가 받았는지"를 되돌리려면 집계값만으로는 부족해 토큰별 결과가 필요하다.
 */
public record MobilePushResult(
		int successCount,
		int failureCount,
		List<String> invalidTokens,
		List<String> successTokens) {

	/** 토큰별 결과가 필요 없는 호출부(단일 구독자 발송 등)를 위한 생성자. */
	public MobilePushResult(int successCount, int failureCount, List<String> invalidTokens) {
		this(successCount, failureCount, invalidTokens, List.of());
	}
}
