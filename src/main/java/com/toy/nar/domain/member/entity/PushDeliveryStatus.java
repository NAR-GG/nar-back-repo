package com.toy.nar.domain.member.entity;

public enum PushDeliveryStatus {
	PENDING,
	SENT,
	FAILED,

	/**
	 * 알림 잠자기 시간대라 푸시를 보내지 않고 알림함(피드)에만 남긴 상태.
	 *
	 * <p>{@link #SENT} 로 두면 대시보드 발송 집계가 실제로 보내지 않은 건을 포함해 거짓말을 한다.
	 * {@link #FAILED} 로 두면 재예약 대상이 되어 잠자기가 끝난 뒤 뒤늦게 시끄러운 푸시가 나간다.
	 * 그래서 둘과 구분되는 종료 상태가 필요하다.</p>
	 */
	SKIPPED_QUIET
}
