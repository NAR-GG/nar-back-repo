package com.toy.nar.app.mobile.push;

import java.util.List;
import java.util.stream.Stream;

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

	/** 잠자기 분할 발송처럼 여러 번 보낸 결과를 하나로 합친다. */
	public MobilePushResult merge(MobilePushResult other) {
		return new MobilePushResult(
				successCount + other.successCount,
				failureCount + other.failureCount,
				concat(invalidTokens, other.invalidTokens),
				concat(successTokens, other.successTokens));
	}

	private static List<String> concat(List<String> left, List<String> right) {
		return Stream.concat(left.stream(), right.stream()).toList();
	}
}
