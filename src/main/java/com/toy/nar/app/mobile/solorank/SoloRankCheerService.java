package com.toy.nar.app.mobile.solorank;

import com.toy.nar.app.mobile.solorank.dto.SoloRankCheerResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus;
import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.GameKey;
import com.toy.nar.domain.participant.repository.PlayerSoloRankGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 홈 솔랭 카드의 응원 버튼. 지금 솔랭 중인 선수에게만 보낼 수 있고, 회원·선수·판 단위로 누적한다.
 * Redis 없이 MySQL upsert 로 충분하다 — 쓰기는 회원 본인 행만 갱신해 경합이 없고, 앱이 탭을 묶어 보낸다.
 */
@Service
@RequiredArgsConstructor
public class SoloRankCheerService {

	/** 한 번에 보낼 수 있는 상한. 사람이 1초에 누를 수 있는 양을 넉넉히 넘는다. */
	static final int MAX_COUNT_PER_REQUEST = 30;

	private final PlayerSoloRankGameRepository gameRepository;
	private final PlayerSoloRankCheerRepository cheerRepository;

	@Transactional
	public SoloRankCheerResponse cheer(Long memberId, Long playerId, int count) {
		if (count < 1 || count > MAX_COUNT_PER_REQUEST) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		List<PlayerSoloRankGame> live = gameRepository.findLive(Set.of(playerId),
				PlayerRiotAccountLiveStatus.IN_RANKED_SOLO,
				LocalDateTime.now().minus(MobileSoloRankStatusService.LIVE_FRESHNESS));
		if (live.isEmpty()) {
			throw new CustomException(ErrorCode.SOLO_RANK_NOT_LIVE);
		}
		String gameId = live.get(0).getGameId();
		cheerRepository.add(playerId, gameId, memberId, count);

		GameKey key = new GameKey(playerId, gameId);
		long total = cheerRepository.totals(List.of(playerId), List.of(gameId)).getOrDefault(key, 0L);
		long mine = cheerRepository.mine(memberId, List.of(playerId), List.of(gameId)).getOrDefault(key, 0L);
		return new SoloRankCheerResponse(total, mine);
	}
}
