package com.toy.nar.app.data.source;

import java.io.InputStream;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.api.services.drive.Drive;
import com.toy.nar.app.analysis.service.CombinationService;
import com.toy.nar.app.data.ingestion.dto.DataIngestionResult;
import com.toy.nar.app.data.source.dto.DataSyncResult;
import com.toy.nar.app.data.ingestion.DataIngestionFacade;
import com.toy.nar.app.schedule.CacheEvictionService;
import com.toy.nar.domain.sync.SyncStatusRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveDataSyncService {

	private final Drive drive;
	private final DataIngestionFacade dataIngestionFacade;
	private final NotificationService notificationService;
	private final CombinationService combinationService;
	private final CacheEvictionService cacheEvictionService;
	private final SyncStatusRepository syncStatusRepository;

	@Value("${google.drive.csv-file-id}")
	private String csvFileId;

	@Scheduled(cron = "0 30 4,10,16,22 * * ?", zone = "Asia/Seoul")
	public void scheduledSyncFromGoogleDrive() {
		try {
			log.info("[Starting] Starting scheduled Google Drive data sync at {}", LocalDateTime.now());
			DataSyncResult result = syncFromGoogleDrive();

			log.info("[Completed] Scheduled sync completed: {} new games added", result.newGamesAdded());

			if (result.isSuccess()) {
				combinationService.updateInfo();
				cacheEvictionService.evictTodayScheduleCache();
				cacheEvictionService.evictTodayMatchDetailsCache();
				notificationService.sendSuccessNotification(result);
			}

		} catch (Exception e) {
			log.error("[Failed] Scheduled sync failed", e);
			// 실패 알림
			notificationService.sendFailureNotification("[Failed] Scheduled sync failed: " + e.getMessage());
		}
	}
	/**
	 * Google Drive에서 최신 CSV를 다운로드하여 DB에 동기화
	 */
	public DataSyncResult syncFromGoogleDrive() {
		log.info("[Starting] Starting Google Drive data sync...");
		long startTime = System.currentTimeMillis();

		try {

			String lastProcessedId = syncStatusRepository.findById("GOOGLE_DRIVE_CSV")
				.map(status -> status.getLastProcessedId())
				.orElse(null);

			// 1. Google Drive에서 스트림으로 다운로드
			InputStream csvStream = drive.files()
				.get(csvFileId)
				.executeMediaAsInputStream();

			// 2. 기존 검증된 로직으로 처리
			DataIngestionResult ingestionResult = dataIngestionFacade.ingestFromStream(csvStream, lastProcessedId);

			// 3. 결과 변환
			DataSyncResult syncResult = DataSyncResult.fromIngestionResult(ingestionResult)
				.toBuilder()
				.processingTimeMs(System.currentTimeMillis() - startTime)
				.source("GOOGLE_DRIVE")
				.build();

			log.info("[Completed] Google Drive sync completed: {}", syncResult.getSummary());
			return syncResult;

		} catch (Exception e) {
			log.error("[Failed] Google Drive sync failed", e);

			// 에러를 래핑하여 DataSyncResult로 반환 (예외 재발생 안함)
			return DataSyncResult.failure(e.getMessage())
				.toBuilder()
				.processingTimeMs(System.currentTimeMillis() - startTime)
				.source("GOOGLE_DRIVE")
				.build();
		}
	}

	/**
	 * 파일 메타데이터만 확인 (연결 테스트용)
	 */
	public String checkFileStatus() {
		try {
			var fileMetadata = drive.files()
				.get(csvFileId)
				.setFields("id,name,size,modifiedTime")
				.execute();

			return String.format("File: %s, Size: %s bytes, Modified: %s",
				fileMetadata.getName(),
				fileMetadata.getSize(),
				fileMetadata.getModifiedTime());
		} catch (Exception e) {
			log.error("[Failed] Failed to check file status", e);
			throw new RuntimeException("[Failed] 파일 상태 확인 실패: " + e.getMessage());
		}
	}
}
