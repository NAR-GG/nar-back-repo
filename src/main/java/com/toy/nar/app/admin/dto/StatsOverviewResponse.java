package com.toy.nar.app.admin.dto;

import java.util.List;

/**
 * 시간축이 없는 대시보드 값 — 퍼널 4단계 + 분포. 기간 필터와 무관하므로 시계열과 분리했다.
 *
 * @param totalMembers      전체 회원
 * @param onboardedMembers  온보딩 완료(onboarded_at 있음)
 * @param subscribedMembers 선수·팀·경기 중 하나라도 구독한 회원
 * @param ratedMembers      평점을 한 번이라도 남긴 회원
 */
public record StatsOverviewResponse(
        long totalMembers,
        long onboardedMembers,
        long subscribedMembers,
        long ratedMembers,
        List<LabelCount> leagues,
        List<LabelCount> teams,
        List<LabelCount> players
) {
    public record LabelCount(String label, long count) {}
}
