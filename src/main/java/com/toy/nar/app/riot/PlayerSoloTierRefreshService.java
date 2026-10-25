package com.toy.nar.app.riot;

import com.toy.nar.app.riot.dto.RiotLeagueEntryResponse;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 추적 중인 계정마다 Riot league-v4 솔랭 티어를 받아 player_riot_account 에 적는다.
 * 크롤링 game_accounts 는 LCK 선수만 채워져서, 해외 선수 계정은 이게 유일한 티어 출처다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerSoloTierRefreshService {

	private final PlayerRiotAccountRepository accountRepository;
	private final RiotApiClient riotApiClient;

	/** @return 실패한 계정 수. 한 계정이 실패해도 나머지는 계속한다. */
	public int refreshAll() {
		List<PlayerRiotAccount> accounts = accountRepository.findAllTrackedAccounts();
		int failed = 0;
		for (PlayerRiotAccount account : accounts) {
			try {
				Optional<RiotLeagueEntryResponse> solo = riotApiClient.findSoloRankEntry(account.getPuuid(),
						account.getPlatform());
				accountRepository.updateSoloTier(account.getId(),
						solo.map(RiotLeagueEntryResponse::tier).orElse(null),
						solo.map(RiotLeagueEntryResponse::rank).orElse(null),
						solo.map(RiotLeagueEntryResponse::leaguePoints).orElse(null),
						LocalDateTime.now());
			} catch (Exception e) {
				failed++;
				log.warn("Solo tier refresh failed account={} platform={}", account.getRiotId(), account.getPlatform(), e);
			}
		}
		log.info("Solo tier refreshed {}/{}", accounts.size() - failed, accounts.size());
		return failed;
	}
}
