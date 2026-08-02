package com.toy.nar.app.admin.dto;

import com.toy.nar.app.admin.dto.StatsSeriesResponse.CountPoint;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 발송량 시계열. 시계열 응답에서 떼어낸 이유는 비용 때문이다 —
 * 알림은 대량 발송이라 행이 35만 개고 기간을 좁혀도 스캔량이 줄지 않는다(모두 최근 몇 주에 몰려 있음).
 * 이 카드만 늦게 채워지고 나머지 대시보드는 먼저 그려지도록 분리했다.
 */
public record StatsNotificationsResponse(
        LocalDateTime from,
        String bucketUnit,
        List<CountPoint> notifications
) {}
