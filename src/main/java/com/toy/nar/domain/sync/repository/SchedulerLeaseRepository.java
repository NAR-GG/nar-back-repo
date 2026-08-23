package com.toy.nar.domain.sync.repository;

import java.time.Duration;

/**
 * 스케줄러 리더 리스(scheduler_lease, 단일 행). 시계는 DB(NOW(3))가 유일한 기준이다 —
 * 파드들의 로컬 시계를 서로 비교하지 않는다.
 */
public interface SchedulerLeaseRepository {

	/**
	 * 리스를 획득하거나 갱신한다. 성공하면 true = 지금 이 홀더가 리더다.
	 *
	 * <p>한 문장 UPDATE 라 원자적이다: 내가 이미 홀더이거나(갱신), 리스가 만료됐을 때(획득)만
	 * 통과한다. 다른 파드가 살아서 갱신 중이면 조건에 안 걸려 0행 — false 를 받는다.</p>
	 */
	boolean acquireOrRenew(String holder, Duration ttl);

	/**
	 * 리스를 즉시 만료시킨다 — 정상 종료 시 후임이 TTL 을 기다리지 않게.
	 *
	 * <p>DELETE 가 아니라 만료로 처리한다. 행이 사라지면 획득 경로에 INSERT 경쟁이 생긴다.</p>
	 */
	void release(String holder);
}
