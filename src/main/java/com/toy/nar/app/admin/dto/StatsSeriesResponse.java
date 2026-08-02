package com.toy.nar.app.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백오피스 대시보드 시계열. 전부 <b>1시간 버킷</b>이고 값이 0인 시간대는 행이 없다(sparse).
 * 일별 차트는 프론트가 같은 날짜끼리 합쳐서 그린다.
 *
 * @param from   조회 시작 시각(이 시각 이후 데이터만)
 * @param bucketUnit 버킷 단위. 지금은 항상 {@code HOUR}
 */
public record StatsSeriesResponse(
        LocalDateTime from,
        String bucketUnit,
        List<CountPoint> signups,
        List<SubscriptionPoint> subscriptions,
        List<CountPoint> notifications
) {
    /** @param bucket {@code 2026-08-02T13:00} (서버 로컬시각) */
    public record CountPoint(String bucket, long count) {}

    public record SubscriptionPoint(String bucket, long player, long team, long match) {}
}
