package com.toy.nar.common.data.service;

import java.io.InputStream;

import org.springframework.stereotype.Service;

import com.google.api.services.drive.Drive;
import com.toy.nar.common.data.dto.DataIngestionResult;
import com.toy.nar.common.data.dto.DataSyncResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveDataSyncService {

	private final Drive drive;
	private final DataIngestionFacade dataIngestionFacade; // 의존성 변경

	private static final String CSV_FILE_ID = "1v6LRphp2kYciU4SXp0PCjEMuev1bDejc";

	/**
	 * Google Drive에서 최신 CSV를 다운로드하여 DB에 동기화
	 */
	public DataSyncResult syncFromGoogleDrive() {
		log.info("🔄 Starting Google Drive data sync...");
		long startTime = System.currentTimeMillis();

		try {
			// 1. Google Drive에서 스트림으로 다운로드
			InputStream csvStream = drive.files()
				.get(CSV_FILE_ID)
				.executeMediaAsInputStream();

			// 2. 기존 검증된 로직으로 처리
			DataIngestionResult ingestionResult = dataIngestionFacade.ingestFromStream(csvStream); // 호출 대상 변경

			// 3. 결과 변환 (1번 방법 사용)
			DataSyncResult syncResult = DataSyncResult.fromIngestionResult(ingestionResult)
				.toBuilder()
				.processingTimeMs(System.currentTimeMillis() - startTime)
				.source("GOOGLE_DRIVE")
				.build();

			log.info("✅ Google Drive sync completed: {}", syncResult.getSummary());
			return syncResult;

		} catch (Exception e) {
			log.error("❌ Google Drive sync failed", e);

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
				.get(CSV_FILE_ID)
				.setFields("id,name,size,modifiedTime")
				.execute();

			return String.format("File: %s, Size: %s bytes, Modified: %s",
				fileMetadata.getName(),
				fileMetadata.getSize(),
				fileMetadata.getModifiedTime());
		} catch (Exception e) {
			log.error("Failed to check file status", e);
			throw new RuntimeException("파일 상태 확인 실패: " + e.getMessage());
		}
	}
}
