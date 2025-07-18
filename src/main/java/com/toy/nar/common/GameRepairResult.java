package com.toy.nar.common;

// GameRepairResult.java
public class GameRepairResult {
	public enum Status { SUCCESS, FAILED, NOT_FOUND }

	private final String gameId;
	private final Status status;
	private final String errorMessage;

	private GameRepairResult(String gameId, Status status, String errorMessage) {
		this.gameId = gameId;
		this.status = status;
		this.errorMessage = errorMessage;
	}

	public static GameRepairResult success(String gameId) {
		return new GameRepairResult(gameId, Status.SUCCESS, null);
	}

	public static GameRepairResult failed(String gameId, String errorMessage) {
		return new GameRepairResult(gameId, Status.FAILED, errorMessage);
	}

	public static GameRepairResult notFound(String gameId) {
		return new GameRepairResult(gameId, Status.NOT_FOUND, "Game not found in CSV");
	}

	public String getGameId() { return gameId; }
	public Status getStatus() { return status; }
	public String getErrorMessage() { return errorMessage; }
}
