package com.toy.nar.common.config.google;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test/drive")
@RequiredArgsConstructor
public class DriveTestController {

	private final DriveTestService driveTestService;

	@GetMapping("/metadata")
	public ResponseEntity<String> testMetadata() {
		driveTestService.testFileMetadata();
		return ResponseEntity.ok("메타데이터 테스트 완료 - 로그 확인");
	}

	@GetMapping("/download")
	public ResponseEntity<String> testDownload() {
		driveTestService.testCsvDownload();
		return ResponseEntity.ok("다운로드 테스트 완료 - 로그 확인");
	}

	@GetMapping("/all")
	public ResponseEntity<String> testAll() {
		driveTestService.runAllTests();
		return ResponseEntity.ok("전체 테스트 완료 - 로그 확인");
	}
}