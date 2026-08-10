package com.toy.nar.domain.member.repository.dto;

import java.time.LocalTime;

/**
 * 잠자기 판정에 필요한 최소 필드만 담는 projection.
 *
 * <p>기기 목록에서 {@code device.getMember()} 로 엔티티를 타면 구독자 수만큼 프록시가
 * 초기화된다(1,500명이면 쿼리 1,500방). 회원 id 집합으로 이 DTO만 한 방에 받아 N+1을 피한다.</p>
 */
public record MemberQuietHours(
		Long memberId,
		boolean enabled,
		LocalTime startTime,
		LocalTime endTime) {
}
