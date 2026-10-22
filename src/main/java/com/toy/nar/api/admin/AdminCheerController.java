package com.toy.nar.api.admin;

import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.CheerMemberRow;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.CheerPlayerRow;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.CheerSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 백오피스 응원 탭 — 홈 솔랭 카드 응원 버튼(player_solo_rank_cheer)의 누적 현황.
 * {@code /api/admin/**} 는 ROLE_ADMIN 보호. days 를 비우면 전체 기간.
 */
@RestController
@RequestMapping("/api/admin/cheers")
@RequiredArgsConstructor
public class AdminCheerController {

    private final PlayerSoloRankCheerRepository cheerRepository;

    @GetMapping("/summary")
    public CheerSummary summary(@RequestParam(required = false) Integer days) {
        return cheerRepository.summary(days);
    }

    /** 응원한 회원 랭킹. sort=totalCount|gameCount|playerCount|firstCheeredAt|lastCheeredAt. */
    @GetMapping("/members")
    public Page<CheerMemberRow> members(@RequestParam(required = false) Integer days,
                                        @RequestParam(required = false) Long playerId,
                                        @RequestParam(required = false) String q,
                                        Pageable pageable) {
        return cheerRepository.members(days, playerId, q == null || q.isBlank() ? null : q.trim(), pageable);
    }

    /** 응원받은 선수 랭킹. sort=totalCount|memberCount|gameCount|lastCheeredAt. */
    @GetMapping("/players")
    public Page<CheerPlayerRow> players(@RequestParam(required = false) Integer days, Pageable pageable) {
        return cheerRepository.players(days, pageable);
    }
}
