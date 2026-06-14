package com.toy.nar.api.mobile.match;

import com.toy.nar.app.mobile.match.MobileLiveGameService;
import com.toy.nar.app.mobile.match.dto.LiveGameChampionsResponse;
import com.toy.nar.app.mobile.match.dto.LiveGameEventsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 라이브 경기 상세", description = "모바일 경기 상세 화면용 라이브 챔피언 픽/밴 및 이벤트 타임라인 API")
@RestController
@RequestMapping("/api/mobile/live/games/{gameId}")
@RequiredArgsConstructor
public class MobileLiveGameController {

	private final MobileLiveGameService liveGameService;

	@Operation(summary = "라이브 챔피언 픽/밴 조회", description = "최신 라이브 상태 기준 블루/레드 팀의 챔피언 픽과 밴 목록을 조회합니다.")
	@GetMapping("/champions")
	public ResponseEntity<LiveGameChampionsResponse> getChampions(@PathVariable String gameId) {
		return ResponseEntity.ok(liveGameService.getChampions(gameId));
	}

	@Operation(summary = "라이브 이벤트 타임라인 조회", description = "킬/오브젝트(드래곤·바론·타워·억제기) 이벤트를 최신순으로 조회합니다.")
	@GetMapping("/events")
	public ResponseEntity<LiveGameEventsResponse> getEvents(@PathVariable String gameId) {
		return ResponseEntity.ok(liveGameService.getEvents(gameId));
	}
}
