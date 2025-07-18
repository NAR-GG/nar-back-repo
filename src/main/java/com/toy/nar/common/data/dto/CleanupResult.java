package com.toy.nar.common.data.dto;

import java.util.Collections;
import java.util.List;

public class CleanupResult {
	private final boolean success;
	private final int deletedGames;
	private final List<String> deletedGameIds;
	private final String message;

	private CleanupResult(boolean success, int deletedGames, List<String> deletedGameIds, String message) {
		this.success = success;
		this.deletedGames = deletedGames;
		this.deletedGameIds = deletedGameIds != null ? deletedGameIds : Collections.emptyList();
		this.message = message;
	}

	public static CleanupResult success(int deletedGames, List<String> deletedGameIds) {
		String message = deletedGames + "개의 불완전한 게임을 삭제했습니다.";
		return new CleanupResult(true, deletedGames, deletedGameIds, message);
	}

	public static CleanupResult noGamesToDelete() {
		return new CleanupResult(true, 0, Collections.emptyList(), "삭제할 불완전한 게임이 없습니다.");
	}

	public static CleanupResult failure(String errorMessage) {
		return new CleanupResult(false, 0, Collections.emptyList(), errorMessage);
	}

	// getters
	public boolean isSuccess() { return success; }
	public int getDeletedGames() { return deletedGames; }
	public List<String> getDeletedGameIds() { return deletedGameIds; }
	public String getMessage() { return message; }
}

