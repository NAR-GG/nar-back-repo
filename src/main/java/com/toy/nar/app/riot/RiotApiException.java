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
}
