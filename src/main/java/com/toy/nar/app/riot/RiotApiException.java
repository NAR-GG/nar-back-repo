package com.toy.nar.app.riot;

public class RiotApiException extends RuntimeException {

	private final int statusCode;

	public RiotApiException(String message, int statusCode, Throwable cause) {
		super(message, cause);
		this.statusCode = statusCode;
	}

	public RiotApiException(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public boolean isRateLimited() {
		return statusCode == 429;
	}

	// 일시 오류: 429(레이트리밋) 또는 5xx(업스트림/Cloudflare 520~ 포함)/타임아웃(500 래핑).
	// 재시도로 자동 복구되는 유형 → 폴에서 개별 알림 대신 스킵·집계.
	public boolean isTransient() {
		return statusCode == 429 || statusCode >= 500;
	}
}
