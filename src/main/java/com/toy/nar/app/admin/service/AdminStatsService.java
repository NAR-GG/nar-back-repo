package com.toy.nar.app.admin.service;

import com.toy.nar.app.admin.dto.StatsNotificationsResponse;
import com.toy.nar.app.admin.dto.StatsOverviewResponse;
import com.toy.nar.app.admin.dto.StatsSeriesResponse;
import com.toy.nar.domain.member.repository.AdminStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 백오피스 대시보드 집계. 조회 전용이라 쓰기 트랜잭션이 없다.
 *
 * <p>기간은 <b>일 단위로 잘라 자정부터</b> 읽는다. 화면의 24시간 롤링 윈도는 마지막 이틀치 시간 버킷에서
 * 프론트가 만들어 쓴다(그래서 최소 조회 기간이 2일).
 *
 * <p>세 응답 모두 1분 캐시다. 운영 지표라 1분 지연은 무해하고, 대신 기간 토글·새로고침·다른 관리자 접속이
 * 전부 캐시 히트가 된다. 특히 알림 집계는 프로덕션에서 초 단위로 걸리는 쿼리다.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private static final int MIN_DAYS = 2;
    private static final int MAX_DAYS = 90;
    private static final int TOP_LIMIT = 10;
    private static final String HOUR = "HOUR";
    private static final DateTimeFormatter BUCKET_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00");

    private final AdminStatsRepository statsRepository;

    @Cacheable(cacheNames = "adminStatsSeries", key = "#days")
    @Transactional(readOnly = true)
    public StatsSeriesResponse series(int days) {
        LocalDateTime from = fromOf(days);

        List<StatsSeriesResponse.CountPoint> signups = statsRepository.signupsByHour(from).stream()
                .map(row -> new StatsSeriesResponse.CountPoint(bucketLabel(row.getBucket()), row.getCnt()))
                .toList();
        List<StatsSeriesResponse.SubscriptionPoint> subscriptions = statsRepository.subscriptionsByHour(from).stream()
                .map(row -> new StatsSeriesResponse.SubscriptionPoint(bucketLabel(row.getBucket()),
                        row.getPlayerCnt(), row.getTeamCnt(), row.getMatchCnt()))
                .toList();

        return new StatsSeriesResponse(from, HOUR, signups, subscriptions);
    }

    @Cacheable(cacheNames = "adminStatsNotifications", key = "#days")
    @Transactional(readOnly = true)
    public StatsNotificationsResponse notifications(int days) {
        LocalDateTime from = fromOf(days);

        List<StatsSeriesResponse.CountPoint> points = statsRepository.notificationsByHour(from).stream()
                .map(row -> new StatsSeriesResponse.CountPoint(bucketLabel(row.getBucket()), row.getCnt()))
                .toList();

        return new StatsNotificationsResponse(from, HOUR, points);
    }

    @Cacheable(cacheNames = "adminStatsOverview", key = "'all'")
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

    private static LocalDateTime fromOf(int days) {
        return LocalDate.now().minusDays(clampDays(days) - 1L).atStartOfDay();
    }

    /** 에폭 시(정수) → {@code 2026-08-02T13:00}. 포맷을 SQL 이 아니라 여기서 하는 이유는 리포지토리 주석 참고. */
    private static String bucketLabel(long epochHour) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochHour * 3600), ZoneId.systemDefault())
                .format(BUCKET_FORMAT);
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
