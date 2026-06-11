package com.toy.nar.api.admin;

import com.toy.nar.app.lolesports.season.LeagueMatchSeasonBackfillService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile({ "local", "dev", "prod" })
@RestController
@RequestMapping("/api/admin/league-match-seasons")
@RequiredArgsConstructor
@Hidden
@Slf4j
public class LeagueMatchSeasonAdminController {

	private final LeagueMatchSeasonBackfillService backfillService;

	/**
	 * league_match 시즌 백필. 기본 dryRun=true 이므로 실제 갱신은 dryRun=false 를 명시해야 한다.
	 * dryRun 집계는 기간이 겹치는 토너먼트에서 중복 계산될 수 있다 (실제 갱신은 중복 없음).
	 */
	@PostMapping("/backfill")
	public ResponseEntity<LeagueMatchSeasonBackfillService.BackfillResult> backfill(
			@RequestParam(defaultValue = "true") boolean dryRun,
			@RequestParam(defaultValue = "false") boolean force) {
		log.info("시즌 백필 요청: dryRun={}, force={}", dryRun, force);
		return ResponseEntity.ok(backfillService.backfill(dryRun, force));
	}
}
