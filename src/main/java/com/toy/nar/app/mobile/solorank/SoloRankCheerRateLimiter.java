package com.toy.nar.app.mobile.solorank;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * 회원별 응원 속도 제한. 초당 20회씩 채워지는 토큰 버킷이고, 앱이 탭을 모아 보내는 걸 받아 주려고
 * 3초 치(60)까지 쌓아 둔다. 매크로로 부풀리거나 요청을 반복해 DB 쓰기·커넥션을 잡는 걸 막는다.
 *
 * <p>ponytail: 웹 파드 JVM 안의 메모리 제한이다. 파드가 여럿이면 파드마다 따로 세고(롤아웃 겹침 중에도
 * 최대 2배), 재기동하면 비운다. 엄격한 상한이 필요해지면 DB·Redis 로 옮긴다.
 */
@Component
public class SoloRankCheerRateLimiter {

	static final int PER_SECOND = 20;
	static final int BURST = 60;

	private static final class Bucket {
		double tokens = BURST;
		long lastNanos;
	}

	private final LongSupplier nanoTime;
	private final Cache<Long, Bucket> buckets = Caffeine.newBuilder()
			.expireAfterAccess(Duration.ofMinutes(1)).maximumSize(50_000).build();

	public SoloRankCheerRateLimiter() {
		this(System::nanoTime);
	}

	SoloRankCheerRateLimiter(LongSupplier nanoTime) {
		this.nanoTime = nanoTime;
	}

	/** {@code count} 만큼 보낼 수 있으면 토큰을 쓰고 true. 모자라면 쓰지 않고 false. */
	public boolean tryConsume(Long memberId, int count) {
		long now = nanoTime.getAsLong();
		Bucket bucket = buckets.get(memberId, id -> {
			Bucket created = new Bucket();
			created.lastNanos = now;
			return created;
		});
		synchronized (bucket) {
			bucket.tokens = Math.min(BURST, bucket.tokens + (now - bucket.lastNanos) / 1e9 * PER_SECOND);
			bucket.lastNanos = now;
			if (bucket.tokens < count) {
				return false;
			}
			bucket.tokens -= count;
			return true;
		}
	}
}
