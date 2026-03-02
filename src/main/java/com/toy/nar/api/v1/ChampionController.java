package com.toy.nar.api.v1;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

	private static final String CHAMPIONS_CACHE_CONTROL = "public, max-age=86400, stale-while-revalidate=604800";

	private final ChampionService championService;
	private final ChampionDataService championDataService;

	@Operation(summary = "전체 챔피언 목록 조회", description = "등록된 모든 챔피언의 정보를 조회합니다. ")
	@GetMapping
	public ResponseEntity<List<ChampionDto>> getAllChampions(
		@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
	) {
		List<ChampionDto> champions = championService.getAllChampions();
		String etag = championService.generateChampionsEtag(champions);

		if (isEtagMatched(ifNoneMatch, etag)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
				.header(HttpHeaders.CACHE_CONTROL, CHAMPIONS_CACHE_CONTROL)
				.header(HttpHeaders.ETAG, etag)
				.build();
		}

		return ResponseEntity.ok()
			.header(HttpHeaders.CACHE_CONTROL, CHAMPIONS_CACHE_CONTROL)
			.header(HttpHeaders.ETAG, etag)
			.body(champions);
	}

	private boolean isEtagMatched(String ifNoneMatch, String currentEtag) {
		if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
			return false;
		}

		String normalizedCurrent = normalizeEtag(currentEtag);
		for (String rawTag : ifNoneMatch.split(",")) {
			String tag = rawTag.trim();
			if ("*".equals(tag)) {
				return true;
			}
			if (normalizedCurrent.equals(normalizeEtag(tag))) {
				return true;
			}
		}
		return false;
	}

	private String normalizeEtag(String etag) {
		if (etag == null) {
			return "";
		}
		String trimmed = etag.trim();
		if (trimmed.startsWith("W/")) {
			return trimmed.substring(2).trim();
		}
		return trimmed;
	}

	@Operation(summary = "챔피언 데이터 동기화 (관리자용)", description = "챔피언 데이터를 가져와 DB를 갱신합니다.")
	@PostMapping("/sync")
	public ResponseEntity<String> syncChampions() {
		championDataService.fetchAndSaveChampions();
		return ResponseEntity.ok("Champion data sync requested.");
	}

	@Operation(summary = "챔피언 로딩 이미지 URL 수동 업데이트", description = "카드 배경용 loading 이미지 URL을 챔피언에 저장합니다.")
	@PostMapping("/{championId}/loading-image")
	public ResponseEntity<String> updateChampionLoadingImage(
			@PathVariable Long championId,
			@RequestParam String imageUrl) {
		championService.updateChampionLoadingImage(championId, imageUrl);
		return ResponseEntity.ok("Champion loading image updated successfully.");
	}

	@Operation(summary = "전체 챔피언 로딩 이미지 URL 일괄 업데이트", description = "모든 챔피언의 loading 이미지 URL을 자동 생성해 저장합니다.")
	@PostMapping("/loading-image/all")
	public ResponseEntity<String> updateAllChampionLoadingImages(
			@RequestParam(defaultValue = "false") boolean overwrite) {
		int updatedCount = championService.updateAllChampionLoadingImages(overwrite);
		return ResponseEntity.ok("Champion loading images updated: " + updatedCount);
	}

}
