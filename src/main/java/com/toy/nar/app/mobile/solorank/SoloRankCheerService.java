package com.toy.nar.app.mobile.solorank;

import com.toy.nar.app.mobile.solorank.dto.SoloRankCheerResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus;
import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.GameKey;
import com.toy.nar.domain.participant.repository.PlayerSoloRankGameRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 홈 솔랭 카드의 응원 버튼. 지금 솔랭 중인 선수에게만 보낼 수 있고, 회원·선수·판 단위로 누적한다.
 * Redis 없이 MySQL upsert 로 충분하다 — 쓰기는 회원 본인 행만 갱신해 경합이 없고, 앱이 탭을 묶어 보내며,
 * 회원당 초당 20회로 속도를 제한한다.
 */
@Service
@RequiredArgsConstructor
public class SoloRankCheerService {

	/** 한 번에 보낼 수 있는 상한. 사람이 1초에 누를 수 있는 양을 넉넉히 넘는다. */
	static final int MAX_COUNT_PER_REQUEST = 30;

	private final PlayerSoloRankGameRepository gameRepository;
	private final PlayerSoloRankCheerRepository cheerRepository;
	private final SoloRankCheerTotals cheerTotals;
	private final SoloRankCheerRateLimiter rateLimiter;

	/**
	 * 선수가 지금 하는 판. 판이 도는 동안 바뀌지 않아서 매 전송마다 조인 쿼리를 태우지 않는다.
	 * 판이 끝난 직후 몇 초는 더 받을 수 있지만 막바지 전송이 늦게 닿는 걸 받아 주는 쪽이 낫다.
	 */
	private final Cache<Long, Optional<String>> liveGameIds = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofSeconds(5)).maximumSize(1_000).build();

	@Transactional
	public SoloRankCheerResponse cheer(Long memberId, Long playerId, int count) {
		if (count < 1 || count > MAX_COUNT_PER_REQUEST) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		if (!rateLimiter.tryConsume(memberId, count)) {
			throw new CustomException(ErrorCode.SOLO_RANK_CHEER_RATE_LIMIT);
		}
		String gameId = liveGameIds.get(playerId, this::findLiveGameId)
				.orElseThrow(() -> new CustomException(ErrorCode.SOLO_RANK_NOT_LIVE));
		cheerRepository.add(playerId, gameId, memberId, count);

		GameKey key = new GameKey(playerId, gameId);
		// 합계는 캐시 값이라 몇 초 늦다. 앱이 내 낙관적 수와 max 로 합쳐 보여서 괜찮다.
		long total = cheerTotals.live(List.of(key)).getOrDefault(key, 0L);
		long mine = cheerRepository.mine(memberId, List.of(playerId), List.of(gameId)).getOrDefault(key, 0L);
		return new SoloRankCheerResponse(total, mine);
	}

	private Optional<String> findLiveGameId(Long playerId) {
		return gameRepository.findLive(Set.of(playerId), PlayerRiotAccountLiveStatus.IN_RANKED_SOLO,
						LocalDateTime.now().minus(MobileSoloRankStatusService.LIVE_FRESHNESS))
				.stream().findFirst().map(PlayerSoloRankGame::getGameId);
	}
}
