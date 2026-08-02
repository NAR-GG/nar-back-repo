package com.toy.nar.app.admin.service;

import com.toy.nar.app.admin.dto.StatsOverviewResponse;
import com.toy.nar.app.admin.dto.StatsSeriesResponse;
import com.toy.nar.domain.member.repository.AdminStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 백오피스 대시보드 집계. 조회 전용이라 쓰기 트랜잭션이 없다.
 *
 * <p>기간은 <b>일 단위로 잘라 자정부터</b> 읽는다. 화면의 24시간 롤링 윈도는 마지막 이틀치 시간 버킷에서
 * 프론트가 만들어 쓴다(그래서 최소 조회 기간이 2일).
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private static final int MIN_DAYS = 2;
    private static final int MAX_DAYS = 90;
    private static final int TOP_LIMIT = 10;

    private final AdminStatsRepository statsRepository;

    @Transactional(readOnly = true)
    public StatsSeriesResponse series(int days) {
        LocalDateTime from = LocalDate.now().minusDays(clampDays(days) - 1L).atStartOfDay();

        List<StatsSeriesResponse.CountPoint> signups = statsRepository.signupsByHour(from).stream()
                .map(row -> new StatsSeriesResponse.CountPoint(row.getBucket(), row.getCnt()))
                .toList();
        List<StatsSeriesResponse.SubscriptionPoint> subscriptions = statsRepository.subscriptionsByHour(from).stream()
                .map(row -> new StatsSeriesResponse.SubscriptionPoint(
                        row.getBucket(), row.getPlayerCnt(), row.getTeamCnt(), row.getMatchCnt()))
                .toList();
        List<StatsSeriesResponse.CountPoint> notifications = statsRepository.notificationsByHour(from).stream()
                .map(row -> new StatsSeriesResponse.CountPoint(row.getBucket(), row.getCnt()))
                .toList();

        return new StatsSeriesResponse(from, "HOUR", signups, subscriptions, notifications);
    }

    @Transactional(readOnly = true)
    public StatsOverviewResponse overview() {
        var totals = statsRepository.memberTotals();
        return new StatsOverviewResponse(
                totals.getTotalMembers(),
                totals.getOnboardedMembers(),
                totals.getSubscribedMembers(),
                totals.getRatedMembers(),
                toLabelCounts(statsRepository.membersByFavoriteLeague()),
                toLabelCounts(statsRepository.topSubscribedTeams(TOP_LIMIT)),
                toLabelCounts(statsRepository.topSubscribedPlayers(TOP_LIMIT)));
    }

    private static List<StatsOverviewResponse.LabelCount> toLabelCounts(
            List<AdminStatsRepository.LabelCount> rows) {
        return rows.stream()
                .map(row -> new StatsOverviewResponse.LabelCount(row.getLabel(), row.getCnt()))
                .toList();
    }

    private static int clampDays(int days) {
        return Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
    }
}
