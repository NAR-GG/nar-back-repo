package com.toy.nar.app.riot;

import com.toy.nar.app.riot.dto.RiotLeagueEntryResponse;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 추적 중인 계정마다 Riot league-v4 솔랭 티어를 받아 player_riot_account 에 적는다.
 * 크롤링 game_accounts 는 LCK 선수만 채워져서, 해외 선수 계정은 이게 유일한 티어 출처다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerSoloTierRefreshService {

	/**
	 * 판이 끝나고 league-v4 LP 가 바뀌기까지 38~70초(2026-10-25 prod 5판 실측). 이만큼 지난 판만 본다 —
	 * 더 일찍 부르면 옛 LP 를 적는다. 스윕이 30초 주기라 실제로는 종료 120~150초 뒤.
	 */
	static final Duration LP_SETTLE = Duration.ofSeconds(120);

	/** 이보다 오래전에 끝난 판은 더 쫓지 않는다. 계속 실패하는 계정이 끝없이 재시도되지 않게 한다. */
	static final Duration LOOKBACK = Duration.ofMinutes(30);

	private final PlayerRiotAccountRepository accountRepository;
	private final RiotApiClient riotApiClient;

	/** @return 실패한 계정 수. 한 계정이 실패해도 나머지는 계속한다. */
	public int refreshAll() {
		List<PlayerRiotAccount> accounts = accountRepository.findAllTrackedAccounts();
		int failed = 0;
		for (PlayerRiotAccount account : accounts) {
			try {
				refresh(account);
			} catch (Exception e) {
				failed++;
				log.warn("Solo tier refresh failed account={} platform={}", account.getRiotId(), account.getPlatform(), e);
			}
		}
		log.info("Solo tier refreshed {}/{}", accounts.size() - failed, accounts.size());
		return failed;
	}

	/**
	 * 최근 끝난 판의 계정 중, 그 판의 LP 가 반영된 뒤로 티어를 못 받은 계정만 갱신한다.
	 * 할 일을 메모리에 들지 않고 DB(판 종료 시각 vs 티어 조회 시각)에서 매번 도출하므로
	 * 배포·리더 교체에도 잃지 않고, 실패하면 다음 스윕이 다시 집는다.
	 *
	 * @return 갱신한 계정 수
	 */
	public int refreshRecentlyEnded(LocalDateTime now) {
		Map<Long, PlayerRiotAccount> due = new LinkedHashMap<>();
		for (PlayerRiotAccountRepository.EndedGame game : accountRepository.findEndedGames(now.minus(LOOKBACK), now.minus(LP_SETTLE))) {
			LocalDateTime checkedAt = game.account().getSoloTierCheckedAt();
			if (checkedAt == null || checkedAt.isBefore(game.endedAt().plus(LP_SETTLE))) {
				due.putIfAbsent(game.account().getId(), game.account());
			}
		}
		int refreshed = 0;
		for (PlayerRiotAccount account : due.values()) {
			try {
				refresh(account);
				refreshed++;
			} catch (Exception e) {
				log.warn("Solo tier refresh after game failed account={}", account.getRiotId(), e);
			}
		}
		return refreshed;
	}

	/** 한 계정. 판이 끝난 계정은 refreshRecentlyEnded 가 따로 부른다(시간당 스윕은 놓친 것을 메운다). */
	public void refresh(PlayerRiotAccount account) {
		Optional<RiotLeagueEntryResponse> solo = riotApiClient.findSoloRankEntry(account.getPuuid(), account.getPlatform());
		accountRepository.updateSoloTier(account.getId(),
				solo.map(RiotLeagueEntryResponse::tier).orElse(null),
				solo.map(RiotLeagueEntryResponse::rank).orElse(null),
				solo.map(RiotLeagueEntryResponse::leaguePoints).orElse(null),
				LocalDateTime.now());
	}
}
