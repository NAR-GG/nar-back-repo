package com.toy.nar.common;

import java.util.ArrayList;
import java.util.List;

// RepairResult.java
public class RepairResult {
	private int initialIncompleteGames = 0;
	private int repairedGames = 0;
	private int failedGames = 0;
	private int notFoundGames = 0;
	private long processingTime = 0;
	private List<String> failedGameDetails = new ArrayList<>();

	public static RepairResult noRepairNeeded() {
		RepairResult result = new RepairResult();
		return result;
	}

	public void merge(GameRepairResult gameResult) {
		switch (gameResult.getStatus()) {
			case SUCCESS -> this.repairedGames++;
			case FAILED -> {
				this.failedGames++;
				this.failedGameDetails.add(gameResult.getGameId() + ": " + gameResult.getErrorMessage());
			}
			case NOT_FOUND -> this.notFoundGames++;
		}
	}

	// getters and setters
	public int getInitialIncompleteGames() { return initialIncompleteGames; }
	public void setInitialIncompleteGames(int initialIncompleteGames) { this.initialIncompleteGames = initialIncompleteGames; }
	public int getRepairedGames() { return repairedGames; }
	public int getFailedGames() { return failedGames; }
	public int getNotFoundGames() { return notFoundGames; }
	public long getProcessingTime() { return processingTime; }
	public void setProcessingTime(long processingTime) { this.processingTime = processingTime; }
	public List<String> getFailedGameDetails() { return failedGameDetails; }
}


