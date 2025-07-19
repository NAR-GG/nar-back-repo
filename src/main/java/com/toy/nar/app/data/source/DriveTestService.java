package com.toy.nar.app.data.source;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriveTestService {

	private final Drive drive;

	private static final String CSV_FILE_ID = "1v6LRphp2kYciU4SXp0PCjEMuev1bDejc";

	/**
	 * Google Drive에서 CSV 파일 메타데이터 가져오기 테스트
	 */
	public void testFileMetadata() {
		try {
			log.info("🔍 파일 메타데이터 조회 테스트 시작");

			File fileMetadata = drive.files()
				.get(CSV_FILE_ID)
				.setFields("id,name,size,modifiedTime,mimeType")
				.execute();

			log.info("✅ 파일 정보:");
			log.info("  - ID: {}", fileMetadata.getId());
			log.info("  - 이름: {}", fileMetadata.getName());
			log.info("  - 크기: {} bytes", fileMetadata.getSize());
			log.info("  - 수정일시: {}", fileMetadata.getModifiedTime());
			log.info("  - MIME 타입: {}", fileMetadata.getMimeType());

		} catch (Exception e) {
			log.error("❌ 메타데이터 조회 실패", e);
		}
	}

	/**
	 * CSV 파일 다운로드 및 샘플 데이터 읽기 테스트
	 */
	public void testCsvDownload() {
		try {
			log.info("📥 CSV 파일 다운로드 테스트 시작");

			// 임시 파일에 다운로드
			java.io.File permanentFile = new java.io.File("/Users/changha/Documents/2025-3-quarter/lol_data.csv");
			permanentFile.getParentFile().mkdirs(); // 디렉토리 생성

			try (FileOutputStream outputStream = new FileOutputStream(permanentFile)) {
				drive.files()
					.get(CSV_FILE_ID)
					.executeMediaAndDownloadTo(outputStream);
			}

			log.info("✅ 파일 저장 완료: {}", permanentFile.getAbsolutePath());

		} catch (Exception e) {
			log.error("❌ CSV 다운로드 실패", e);
		}
	}

	/**
	 * CSV 파일의 처음 몇 줄 읽어보기
	 */
	private void readCsvSample(java.io.File csvFile) {
		try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {

			// 헤더 읽기
			String[] headers = reader.readNext();
			if (headers != null) {
				log.info("📋 CSV 헤더 ({} 컬럼):", headers.length);
				for (int i = 0; i < Math.min(headers.length, 10); i++) {
					log.info("  [{}] {}", i, headers[i]);
				}
				if (headers.length > 10) {
					log.info("  ... 그 외 {} 개 컬럼", headers.length - 10);
				}
			}

			// 샘플 데이터 읽기
			List<String[]> sampleRows = new ArrayList<>();
			String[] row;
			int count = 0;

			while ((row = reader.readNext()) != null && count < 5) {
				sampleRows.add(row);
				count++;
			}

			log.info("📄 샘플 데이터 ({} 행):", sampleRows.size());
			for (int i = 0; i < sampleRows.size(); i++) {
				String[] sampleRow = sampleRows.get(i);
				log.info("  Row {}: {}", i + 1,
					java.util.Arrays.toString(
						java.util.Arrays.copyOf(sampleRow, Math.min(5, sampleRow.length))
					) + (sampleRow.length > 5 ? "..." : ""));
			}

		} catch (Exception e) {
			log.error("❌ CSV 샘플 읽기 실패", e);
		}
	}

	/**
	 * 전체 테스트 실행
	 */
	public void runAllTests() {
		log.info("🚀 Google Drive 연동 테스트 시작 - {}", LocalDateTime.now());

		testFileMetadata();
		testCsvDownload();

		log.info("🏁 테스트 완료");
	}
}