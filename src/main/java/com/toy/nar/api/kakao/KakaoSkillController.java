package com.toy.nar.api.kakao;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.api.kakao.dto.KakaoSkillRequest;
import com.toy.nar.api.kakao.dto.KakaoSkillResponse;
import com.toy.nar.app.kakao.KakaoMatchThumbnailService;
import com.toy.nar.app.kakao.KakaoScheduleSkillService;

import java.time.Duration;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/kakao/skills")
@RequiredArgsConstructor
public class KakaoSkillController {

	private final KakaoScheduleSkillService kakaoScheduleSkillService;
	private final KakaoMatchThumbnailService kakaoMatchThumbnailService;

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

	@GetMapping(value = "/images/matches/{matchId}.svg", produces = "image/svg+xml")
	public ResponseEntity<String> getMatchThumbnail(@PathVariable String matchId) {
		return ResponseEntity.ok()
				.contentType(MediaType.valueOf("image/svg+xml"))
				.cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
				.body(kakaoMatchThumbnailService.renderMatchThumbnailSvg(matchId));
	}
}
