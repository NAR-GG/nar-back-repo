package com.toy.nar.api.mobile.match;

import com.toy.nar.app.mobile.match.MobileLiveGameService;
import com.toy.nar.app.mobile.match.dto.LiveGameChampionsResponse;
import com.toy.nar.app.mobile.match.dto.LiveGameEventsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "Mobile. 라이브 경기 상세", description = "모바일 경기 상세 화면용 라이브 챔피언 픽/밴 및 이벤트 타임라인 API")
@RestController
@RequestMapping("/api/mobile/live/games/{gameId}")
@RequiredArgsConstructor
public class MobileLiveGameController {

	private final MobileLiveGameService liveGameService;

	@Operation(summary = "라이브 경기 데이터 조회", description = "최신 라이브 상태 기준 픽/밴, 선수 스코어보드(KDA·CS·골드·아이템·룬), 팀 합산, 오브젝트 집계를 한 번에 조회합니다.")
	@GetMapping("/champions")
	public ResponseEntity<LiveGameChampionsResponse> getChampions(@PathVariable String gameId) {
		// 수집은 5초 주기지만 앱이 짧은 간격으로 폴링해도 서버가 버티도록 10초 캐시를 둔다.
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)))
				.body(liveGameService.getChampions(gameId));
	}

	@Operation(summary = "라이브 이벤트 타임라인 조회", description = "킬/오브젝트(드래곤·바론·타워·억제기) 이벤트를 최신순으로 조회합니다.")
	@GetMapping("/events")
	public ResponseEntity<LiveGameEventsResponse> getEvents(@PathVariable String gameId) {
		return ResponseEntity.ok(liveGameService.getEvents(gameId));
	}
}
