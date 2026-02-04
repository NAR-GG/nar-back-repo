package com.toy.nar.api.v1;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.analysis.dto.TeamRankingFilterDto;
import com.toy.nar.app.analysis.dto.TeamRankingResponseDto;
import com.toy.nar.app.analysis.service.TeamRankingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "5. 팀 랭킹", description = "팀 랭킹 및 모스트 픽 데이터를 제공합니다.")
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamRankingController {

    private final TeamRankingService teamRankingService;

    @Operation(summary = "팀 랭킹 조회", description = "필터 기준에 따른 팀 랭킹과 라인별 모스트 픽 챔피언을 조회합니다.")
    @GetMapping("/rankings")
    public ResponseEntity<TeamRankingResponseDto> getTeamRankings(
            @Parameter(description = "경기 연도 (예: 2026)") @RequestParam(value = "year", required = false) Optional<Integer> year,

            @Parameter(description = "스플릿 목록 (예: Round 1-2, Cup)") @RequestParam(value = "splits", required = false) Optional<List<String>> splits,

            @Parameter(description = "리그 이름 목록 (예: LCK, LPL)") @RequestParam(value = "leagueNames", required = false) Optional<List<String>> leagueNames,

            @Parameter(description = "패치 버전 (예: 14.1)") @RequestParam(value = "patch", required = false) Optional<String> patch,

            @Parameter(description = "진영 필터 (ALL, Blue, Red)") @RequestParam(value = "side", defaultValue = "ALL") String side) {
        TeamRankingFilterDto filter = TeamRankingFilterDto.builder()
                .year(year.orElse(null))
                .splits(splits.orElse(null))
                .leagueNames(leagueNames.orElse(null))
                .patch(patch.orElse(null))
                .side(side)
                .build();

        TeamRankingResponseDto response = teamRankingService.getTeamRankings(filter);
        return ResponseEntity.ok(response);
    }
}
