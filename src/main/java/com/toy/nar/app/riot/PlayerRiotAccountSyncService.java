package com.toy.nar.app.riot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.riot.dto.PlayerRiotAccountSyncResult;
import com.toy.nar.app.riot.dto.RiotAccountResolveResponse;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerRiotAccountSyncService {

	private static final String KR_PLATFORM = "KR";

	private final PlayerRepository playerRepository;
	private final PlayerRiotAccountRepository playerRiotAccountRepository;
	private final RiotApiClient riotApiClient;
	private final RiotMonitorProperties riotMonitorProperties;
	private final ObjectMapper objectMapper;
	private final PlatformTransactionManager transactionManager;

	public PlayerRiotAccountSyncResult syncPrimaryAccounts() {
		riotApiClient.assertConfigured();

		List<Player> players = playerRepository.findPlayersByLeagueName(riotMonitorProperties.getTargetLeague());
		List<String> skippedPlayers = new ArrayList<>();
		List<String> failedPlayers = new ArrayList<>();
		int syncedCount = 0;

		for (Player player : players) {
			try {
				Optional<PrimaryAccountCandidate> candidateOptional = extractPrimaryKrAccount(player);
				if (candidateOptional.isEmpty()) {
					skippedPlayers.add(player.getName());
					continue;
				}

				PrimaryAccountCandidate candidate = candidateOptional.get();
				syncSinglePlayerAccount(player, candidate);
				syncedCount++;
			} catch (IllegalArgumentException e) {
				failedPlayers.add(player.getName());
				log.warn("Invalid Riot ID format for player={} rawGameAccounts={}", player.getName(), player.getGameAccounts(), e);
			} catch (Exception e) {
				failedPlayers.add(player.getName());
				log.warn("Failed to sync Riot account for player={}", player.getName(), e);
			}
		}

		return new PlayerRiotAccountSyncResult(
				players.size(),
				syncedCount,
				skippedPlayers.size(),
				failedPlayers.size(),
				skippedPlayers,
				failedPlayers);
	}

	private void syncSinglePlayerAccount(Player player, PrimaryAccountCandidate candidate) {
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
		transactionTemplate.executeWithoutResult(status -> {
			RiotIdParser.ParsedRiotId parsedRiotId = RiotIdParser.parse(candidate.riotId())
					.orElseThrow(() -> new IllegalArgumentException("Invalid Riot ID: " + candidate.riotId()));

			RiotAccountResolveResponse accountResponse = riotApiClient.resolveAccountByRiotId(
					parsedRiotId.gameName(),
					parsedRiotId.tagLine());

			PlayerRiotAccount playerRiotAccount = playerRiotAccountRepository.findByPlayerId(player.getId())
					.orElseGet(() -> PlayerRiotAccount.builder()
							.player(player)
							.riotId(parsedRiotId.normalizedRiotId())
							.gameName(accountResponse.gameName())
							.tagLine(accountResponse.tagLine())
							.platform(KR_PLATFORM)
							.puuid(accountResponse.puuid())
							.summonerId("")
							.primaryAccount(true)
							.enabled(true)
							.liveStatus(PlayerRiotAccountLiveStatus.OFFLINE)
							.build());

			playerRiotAccount.updateResolvedAccount(
					parsedRiotId.normalizedRiotId(),
					accountResponse.gameName(),
					accountResponse.tagLine(),
					KR_PLATFORM,
					accountResponse.puuid());
			playerRiotAccount.markPrimaryAccount(true);
			playerRiotAccount.setEnabled(true);
			playerRiotAccountRepository.saveAndFlush(playerRiotAccount);
		});
	}

	private Optional<PrimaryAccountCandidate> extractPrimaryKrAccount(Player player) {
		if (player.getGameAccounts() == null || player.getGameAccounts().isBlank()) {
			return Optional.empty();
		}

		try {
			List<Map<String, Object>> accounts = objectMapper.readValue(
					player.getGameAccounts(),
					new TypeReference<List<Map<String, Object>>>() {
					});
			if (accounts == null || accounts.isEmpty()) {
				return Optional.empty();
			}

			for (Map<String, Object> account : accounts) {
				String region = toText(account.get("region"));
				String riotId = toText(account.get("riotId"));
				if (KR_PLATFORM.equalsIgnoreCase(region) && riotId != null) {
					return Optional.of(new PrimaryAccountCandidate(region.toUpperCase(), riotId));
				}
			}

			if (accounts.size() == 1) {
				String riotId = toText(accounts.get(0).get("riotId"));
				if (riotId != null) {
					log.info("Using single game account without region metadata for player={}", player.getName());
					return Optional.of(new PrimaryAccountCandidate(KR_PLATFORM, riotId));
				}
			}
			return Optional.empty();
		} catch (Exception e) {
			log.warn("Failed to parse game accounts for player={}", player.getName(), e);
			return Optional.empty();
		}
	}

	private String toText(Object value) {
		return value == null ? null : value.toString().trim();
	}

	private record PrimaryAccountCandidate(String region, String riotId) {
	}
}
