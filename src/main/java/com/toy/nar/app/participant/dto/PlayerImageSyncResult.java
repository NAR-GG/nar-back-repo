package com.toy.nar.app.participant.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PlayerImageSyncResult {
	private int totalTarget;
	private int successCount;
	private int failCount;
	private List<String> failedPlayerNames;
}
