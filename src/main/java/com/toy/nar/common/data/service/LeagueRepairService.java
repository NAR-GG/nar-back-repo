package com.toy.nar.common.data.service;


import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.common.data.dto.DataSyncResult;
import com.toy.nar.game.entity.Game;
import com.toy.nar.game.repository.GameRepository;
import com.toy.nar.participant.entity.Champion;
import com.toy.nar.participant.entity.Player;
import com.toy.nar.participant.entity.Team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeagueRepairService {

	private final GameRepository gameRepository;
	private final GoogleDriveDataSyncService googleDriveDataSyncService;
	private final LeagueCleanupService cleanupService;

	public void repairEWCData() {
		try {
			log.info("🔧 Starting EWC data repair...");

			// 1. 안전한 방법으로 EWC 데이터 삭제
			cleanupService.deleteLeagueDataSafely("EWC");

			// 2. 다시 동기화하여 올바른 데이터 생성
			googleDriveDataSyncService.syncFromGoogleDrive();

			log.info("✅ EWC data repair completed successfully");

		} catch (Exception e) {
			log.error("❌ EWC repair failed", e);
			throw new RuntimeException("EWC 데이터 복구 실패: " + e.getMessage());
		}
	}

}