package com.toy.nar.domain.sync.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class SchedulerLeaseRepositoryImpl implements SchedulerLeaseRepository {

	private final JdbcTemplate jdbcTemplate;

	@Override
	public boolean acquireOrRenew(String holder, Duration ttl) {
		// 밀리초 단위로 넘긴다 — TTL 이 초 미만 정밀도를 가질 일은 없지만 DATETIME(3) 과 맞춘다.
		return jdbcTemplate.update(
				"UPDATE scheduler_lease"
						+ " SET holder = ?, expires_at = TIMESTAMPADD(MICROSECOND, ?, NOW(3))"
						+ " WHERE id = 1 AND (holder = ? OR expires_at < NOW(3))",
				holder, ttl.toNanos() / 1_000, holder) == 1;
	}

	@Override
	public void release(String holder) {
		jdbcTemplate.update(
				"UPDATE scheduler_lease SET expires_at = NOW(3) - INTERVAL 1 SECOND"
						+ " WHERE id = 1 AND holder = ?",
				holder);
	}
}
