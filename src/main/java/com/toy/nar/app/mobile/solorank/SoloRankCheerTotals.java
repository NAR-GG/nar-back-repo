package com.toy.nar.app.mobile.solorank;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.GameKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 판 응원 합계 캐시. 5초 폴링마다 모든 사용자가 같은 판의 합계를 SUM 으로 다시 세지 않게 한다.
 * 응원자가 많은 판일수록 SUM 이 커지므로 라이브는 몇 초만 재사용한다 — 앱은 내 낙관적 수와 max 로
 * 합쳐 보여서 몇 초 늦은 합계가 숫자를 줄이지 않는다.
 *
 * <p>웹 파드 JVM 안에만 있는 캐시다. 값이 몇 초 낡아도 되는 표시용이라 파드마다 달라도 상관없다.
 */
@Component
@RequiredArgsConstructor
public class SoloRankCheerTotals {

	static final Duration LIVE_TTL = Duration.ofSeconds(3);
	/** 끝난 판은 더 늘지 않는다(솔랭 중이 아니면 응원을 받지 않는다). 막바지 전송이 늦게 닿을 여유만 둔다. */
	static final Duration FINISHED_TTL = Duration.ofMinutes(10);

	private final PlayerSoloRankCheerRepository cheerRepository;

	private final Cache<GameKey, Long> live = Caffeine.newBuilder()
			.expireAfterWrite(LIVE_TTL).maximumSize(2_000).build();
	private final Cache<GameKey, Long> finished = Caffeine.newBuilder()
			.expireAfterWrite(FINISHED_TTL).maximumSize(5_000).build();

	public Map<GameKey, Long> live(Collection<GameKey> keys) {
		return load(live, keys);
	}

	public Map<GameKey, Long> finished(Collection<GameKey> keys) {
		return load(finished, keys);
	}

	private Map<GameKey, Long> load(Cache<GameKey, Long> cache, Collection<GameKey> keys) {
		Map<GameKey, Long> result = new HashMap<>();
		List<GameKey> missing = new ArrayList<>();
		for (GameKey key : keys) {
			Long cached = cache.getIfPresent(key);
			if (cached == null) {
				missing.add(key);
			} else {
				result.put(key, cached);
			}
		}
		if (!missing.isEmpty()) {
			Map<GameKey, Long> fresh = cheerRepository.totals(
					missing.stream().map(GameKey::playerId).distinct().toList(),
					missing.stream().map(GameKey::gameId).distinct().toList());
			for (GameKey key : missing) {
				long total = fresh.getOrDefault(key, 0L);
				cache.put(key, total);
				result.put(key, total);
			}
		}
		return result;
	}
}
