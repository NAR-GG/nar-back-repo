package com.toy.nar.app.player;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PlayerProfileSyncResult {
    private int totalCount;
    private int successCount;
    private int failCount;
    private List<String> failedPlayers; // 크롤링 실패한 player_name 목록
}
