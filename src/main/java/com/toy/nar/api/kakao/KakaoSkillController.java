package com.toy.nar.api.kakao;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.api.kakao.dto.KakaoSkillRequest;
import com.toy.nar.api.kakao.dto.KakaoSkillResponse;
import com.toy.nar.app.kakao.KakaoScheduleSkillService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/kakao/skills")
@RequiredArgsConstructor
public class KakaoSkillController {

	private final KakaoScheduleSkillService kakaoScheduleSkillService;

	@PostMapping("/schedule")
	public ResponseEntity<KakaoSkillResponse> getSchedule(@RequestBody(required = false) KakaoSkillRequest request) {
		KakaoSkillRequest safeRequest = request != null ? request : new KakaoSkillRequest(null);
		return ResponseEntity.ok(kakaoScheduleSkillService.handleSchedule(safeRequest));
	}

	@PostMapping("/lck-schedule")
	public ResponseEntity<KakaoSkillResponse> getLckSchedule(@RequestBody(required = false) KakaoSkillRequest request) {
		KakaoSkillRequest safeRequest = request != null ? request : new KakaoSkillRequest(null);
		return ResponseEntity.ok(kakaoScheduleSkillService.handleSchedule(safeRequest));
	}
}
