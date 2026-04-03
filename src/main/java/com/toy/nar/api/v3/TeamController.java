package com.toy.nar.api.v3;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.analysis.dto.TeamRadarResponse;
import com.toy.nar.app.analysis.dto.TeamDetailStatsResponse;
import com.toy.nar.app.analysis.dto.TeamDashboardResponse;
import com.toy.nar.app.analysis.dto.TeamProfileHeaderResponse;
import com.toy.nar.app.analysis.dto.TeamScatterResponse;
import com.toy.nar.app.analysis.service.TeamDashboardService;
import com.toy.nar.app.analysis.service.TeamDetailStatsService;
import com.toy.nar.app.analysis.service.TeamProfileService;
import com.toy.nar.app.analysis.service.TeamRadarService;
import com.toy.nar.app.analysis.service.TeamScatterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 팀 통계 API 컨트롤러
 */
@Tag(name = "팀 분석", description = "팀별 상세 통계 및 레이더 차트 API")
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamRadarService teamRadarService;
    private final TeamScatterService teamScatterService;
    private final TeamDetailStatsService teamDetailStatsService;
    private final TeamProfileService teamProfileService;
    private final TeamDashboardService teamDashboardService;

    @Operation(summary = "팀 레이더 차트 통계", description = "팀의 22개 지표 레이더 차트 데이터를 반환합니다. 리그 평균과 비교할 수 있습니다.")
    @GetMapping("/{teamId}/radar")
    public ResponseEntity<TeamRadarResponse> getTeamRadarStats(
            @Parameter(description = "팀 ID") @PathVariable Long teamId,
            @Parameter(description = "리그명 (기본값: LCK)", example = "LCK") @RequestParam(defaultValue = "LCK") String league,
            @Parameter(description = "연도 (기본값: 2026)", example = "2026") @RequestParam(defaultValue = "2026") Integer year,
            @Parameter(description = "스플릿 (예: Round 1-2)") @RequestParam(required = false) String split,
            @Parameter(description = "패치 (예: 14.1)") @RequestParam(required = false) String patch,
            @Parameter(description = "진영 필터 (ALL, BLUE, RED)", example = "ALL") @RequestParam(defaultValue = "ALL") String side) {

        TeamRadarResponse response = teamRadarService.getTeamRadarStats(teamId, league, year, split, patch, side);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "팀 지표 스캐터 차트", description = "리그/연도 기준으로 팀별 승률(y축)과 평균 지표(x축: ALL, KILLS, GOLD, OBJECTIVES) 데이터를 반환합니다.")
    @GetMapping("/scatter")
    public ResponseEntity<TeamScatterResponse> getTeamScatterStats(
            @Parameter(description = "연도 (기본값: 2026)", example = "2026") @RequestParam(defaultValue = "2026") Integer year,
            @Parameter(description = "리그명 (기본값: LCK)", example = "LCK") @RequestParam(defaultValue = "LCK") String league,
            @Parameter(description = "스플릿 (예: Round 1-2)") @RequestParam(required = false) String split,
            @Parameter(description = "패치 (예: 14.1)") @RequestParam(required = false) String patch,
            @Parameter(description = "진영 필터 (ALL, BLUE, RED)", example = "ALL") @RequestParam(defaultValue = "ALL") String side,
            @Parameter(description = "x축 지표 (ALL, KILLS, GOLD, OBJECTIVES)", example = "ALL") @RequestParam(defaultValue = "ALL") String metric) {

        TeamScatterResponse response = teamScatterService.getTeamScatterStats(league, year, split, patch, side, metric);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "팀 경기 데이터 상세", description = "팀 승률 기준 내림차순으로 매치/세트 전적과 킬/골드/오브젝트/퍼스트 오브젝트 지표를 반환합니다.")
    @GetMapping("/detail-stats")
    public ResponseEntity<TeamDetailStatsResponse> getTeamDetailStats(
            @Parameter(description = "연도 (기본값: 2026)", example = "2026") @RequestParam(defaultValue = "2026") Integer year,
            @Parameter(description = "리그명 (기본값: LCK)", example = "LCK") @RequestParam(defaultValue = "LCK") String league,
            @Parameter(description = "스플릿 (예: Round 1-2)") @RequestParam(required = false) String split,
            @Parameter(description = "패치 (예: 14.1)") @RequestParam(required = false) String patch,
            @Parameter(description = "진영 필터 (ALL, BLUE, RED)", example = "ALL") @RequestParam(defaultValue = "ALL") String side) {

        TeamDetailStatsResponse response = teamDetailStatsService.getTeamDetailStats(league, year, split, patch, side);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "팀 프로필 헤더", description = "팀명/로고와 최근 경기 일정 3개를 반환합니다.")
    @GetMapping("/{teamId}/profile-header")
    public ResponseEntity<TeamProfileHeaderResponse> getTeamProfileHeader(
            @Parameter(description = "팀 ID") @PathVariable Long teamId,
            @Parameter(description = "리그명 (기본값: LCK)", example = "LCK") @RequestParam(defaultValue = "LCK") String league) {

        TeamProfileHeaderResponse response = teamProfileService.getProfileHeader(teamId, league);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "팀 페이지 대시보드", description = "게임요약, 선수기록, 밴당한/밴한 챔피언, 플레이한 챔피언을 필터 기준으로 한 번에 반환합니다.")
    @GetMapping("/{teamId}/dashboard")
    public ResponseEntity<TeamDashboardResponse> getTeamDashboard(
            @Parameter(description = "팀 ID") @PathVariable Long teamId,
            @Parameter(description = "리그명 (기본값: LCK)", example = "LCK") @RequestParam(defaultValue = "LCK") String league,
            @Parameter(description = "연도 (기본값: 2026)", example = "2026") @RequestParam(defaultValue = "2026") Integer year,
            @Parameter(description = "스플릿 (예: Round 1-2)") @RequestParam(required = false) String split,
            @Parameter(description = "패치 (예: 14.1)") @RequestParam(required = false) String patch,
            @Parameter(description = "진영 필터 (ALL, BLUE, RED) - 선수기록(playerRecords)에만 적용", example = "ALL") @RequestParam(defaultValue = "ALL") String side) {

        TeamDashboardResponse response = teamDashboardService.getTeamDashboard(teamId, league, year, split, patch, side);
        return ResponseEntity.ok(response);
    }
}
