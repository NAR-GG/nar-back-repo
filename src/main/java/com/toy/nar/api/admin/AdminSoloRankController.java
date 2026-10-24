package com.toy.nar.api.admin;

import com.toy.nar.app.riot.SoloRankTeamBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 솔랭 이력 관리. {@code /api/admin/**} 는 ROLE_ADMIN 보호.
 */
@RestController
@RequestMapping("/api/admin/solo-rank")
@RequiredArgsConstructor
public class AdminSoloRankController {

    /** 동기 요청이라 Cloudflare 100초 안에 끝나게 한 번에 도는 판 수를 묶는다. 남으면 다시 부른다. */
    private static final int MAX_LIMIT = 100;

    private final SoloRankTeamBackfillService teamBackfillService;

    /** 최근 days 일, 추적 선수 2명 이상이 함께한 판 중 팀 미기록 판에 match-v5 로 팀·플랫폼을 채운다. */
    @PostMapping("/team-backfill")
    public SoloRankTeamBackfillService.Result teamBackfill(@RequestParam(defaultValue = "7") int days,
                                                           @RequestParam(defaultValue = "100") int limit) {
        return teamBackfillService.backfill(days, Math.min(Math.max(limit, 1), MAX_LIMIT));
    }
}
