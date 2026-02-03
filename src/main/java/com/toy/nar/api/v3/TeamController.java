package com.toy.nar.api.v3;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.analysis.dto.TeamRadarResponse;
import com.toy.nar.app.analysis.service.TeamRadarService;

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

    @Operation(summary = "팀 레이더 차트 통계", description = "팀의 22개 지표 레이더 차트 데이터를 반환합니다. 리그 평균과 비교할 수 있습니다.")
    @GetMapping("/{teamId}/radar")
    public ResponseEntity<TeamRadarResponse> getTeamRadarStats(
            @Parameter(description = "팀 ID") @PathVariable Long teamId,
            @Parameter(description = "연도 (기본값: 2026)", example = "2026") @RequestParam(defaultValue = "2026") int year) {

        TeamRadarResponse response = teamRadarService.getTeamRadarStats(teamId, year);
        return ResponseEntity.ok(response);
    }
}
