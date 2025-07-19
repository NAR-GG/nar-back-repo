package com.toy.nar.common.data.controller;

import com.toy.nar.common.data.service.DataVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/data-verification")
@RequiredArgsConstructor
public class DataVerificationController {

	private final DataVerificationService dataVerificationService;

	/**
	 * DB에 있는 모든 리그, 게임, 참가자, 밴 데이터의 정합성을
	 * 종합적으로 검사하고 결과를 리포트합니다.
	 */
	@GetMapping("/full-report")
	public ResponseEntity<DataVerificationService.VerificationReport> getFullVerificationReport() {
		DataVerificationService.VerificationReport report = dataVerificationService.verifyAllData();
		return ResponseEntity.ok(report);
	}
}