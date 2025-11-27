package com.toy.nar.api.v1;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.data.source.ChampionDataService;
import com.toy.nar.app.participant.dto.ChampionDto;
import com.toy.nar.app.participant.service.ChampionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "1.2 챔피언 관리", description = "챔피언 목록 조회 기능을 제공합니다.")
@RestController
@RequestMapping("/api/champions")
@RequiredArgsConstructor
public class ChampionController {

	private final ChampionService championService;
	private final ChampionDataService championDataService;

	@Operation(summary = "전체 챔피언 목록 조회", description = "등록된 모든 챔피언의 정보를 조회합니다. ")
	@GetMapping
	public ResponseEntity<List<ChampionDto>> getAllChampions() {
		List<ChampionDto> champions = championService.getAllChampions();
		return ResponseEntity.ok(champions);
	}

	@Operation(summary = "챔피언 데이터 동기화 (관리자용)", description = "챔피언 데이터를 가져와 DB를 갱신합니다.")
	@PostMapping("/sync")
	public ResponseEntity<String> syncChampions() {
		championDataService.fetchAndSaveChampions();
		return ResponseEntity.ok("Champion data sync requested.");
	}

}
