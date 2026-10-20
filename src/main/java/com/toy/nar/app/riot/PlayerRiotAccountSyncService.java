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
import java.util.Objects;
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

		List<Player> players = playerRepository.findSoloRankSyncTargets(riotMonitorProperties.getTargetLeague());
		List<String> skippedPlayers = new ArrayList<>();
		List<String> failedPlayers = new ArrayList<>();
		int syncedCount = 0;

		for (Player player : players) {
			try {
				Optional<PrimaryAccountCandidate> candidateOptional = extractPrimaryAccount(player);
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
		transactionTemplate.executeWithoutResult(status -> resolveAndPersist(player, candidate.riotId(), candidate.platform()));
	}

	// 현재 트랜잭션에 참여해 Riot ID를 검증·해석하고 PlayerRiotAccount를 저장한다.
	// 신규 선수 등록처럼 player가 아직 커밋 전인 경우 REQUIRES_NEW를 쓰면 FK가 안 보이므로 이 경로를 쓴다.
	public void resolveAndSaveInCurrentTransaction(Player player, String riotId, String platform) {
		riotApiClient.assertConfigured();
		resolveAndPersist(player, riotId, platform);
	}

	private void resolveAndPersist(Player player, String riotId, String platform) {
		String normalizedPlatform = RiotPlatform.toPlatform(platform);
		RiotIdParser.ParsedRiotId parsedRiotId = RiotIdParser.parse(riotId)
				.orElseThrow(() -> new IllegalArgumentException("Invalid Riot ID: " + riotId));

		RiotAccountResolveResponse accountResponse = riotApiClient.resolveAccountByRiotId(
				parsedRiotId.gameName(),
				parsedRiotId.tagLine());

		PlayerRiotAccount playerRiotAccount = playerRiotAccountRepository.findByPlayerIdAndPrimaryAccountTrue(player.getId())
				.orElseGet(() -> PlayerRiotAccount.builder()
						.player(player)
						.riotId(parsedRiotId.normalizedRiotId())
						.gameName(accountResponse.gameName())
						.tagLine(accountResponse.tagLine())
						.platform(normalizedPlatform)
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
				normalizedPlatform,
				accountResponse.puuid());
		playerRiotAccount.markPrimaryAccount(true);
		playerRiotAccount.setEnabled(true);
		playerRiotAccountRepository.saveAndFlush(playerRiotAccount);
	}

	// 기간 한정 보조 계정(월즈 부트캠프 현지 계정 등)을 주계정 옆에 추가한다. 현재 트랜잭션에 참여한다.
	// primary=false 라 일일 sync·구독 쿼리(전부 primary_account = true 조건)는 이 행을 보지 않고, 라이브 폴링만 함께 돈다.
	// 이미 등록된 계정(같은 puuid)이면 새 행 대신 그 행을 다시 켠다. 끝나면 enabled=false 로 끈다.
	public PlayerRiotAccount addSecondaryAccount(Player player, String riotId, String platform) {
		riotApiClient.assertConfigured();
		String normalizedPlatform = RiotPlatform.toPlatform(platform);
		RiotIdParser.ParsedRiotId parsedRiotId = RiotIdParser.parse(riotId)
				.orElseThrow(() -> new IllegalArgumentException("Invalid Riot ID: " + riotId));

		RiotAccountResolveResponse accountResponse = riotApiClient.resolveAccountByRiotId(
				parsedRiotId.gameName(),
				parsedRiotId.tagLine());

		PlayerRiotAccount account = playerRiotAccountRepository.findByPuuid(accountResponse.puuid())
				.orElseGet(() -> PlayerRiotAccount.builder()
						.player(player)
						.riotId(parsedRiotId.normalizedRiotId())
						.gameName(accountResponse.gameName())
						.tagLine(accountResponse.tagLine())
						.platform(normalizedPlatform)
						.puuid(accountResponse.puuid())
						.summonerId("")
						.primaryAccount(false)
						.enabled(true)
						.liveStatus(PlayerRiotAccountLiveStatus.OFFLINE)
						// 모니터는 lastCheckedMatchId 가 null 인 계정의 첫 게임을 기준선으로만 잡고 알리지 않는다.
						// 보조 계정은 등록 직후 첫 게임부터 알려야 하므로 빈 값으로 시작한다(어떤 gameId 와도 다르다).
						.lastCheckedMatchId("")
						.build());
		if (!Objects.equals(account.getPlayer().getId(), player.getId())) {
			throw new IllegalArgumentException(
					"다른 선수에 등록된 계정입니다: " + riotId + " (player=" + account.getPlayer().getName() + ")");
		}

		account.updateResolvedAccount(
				parsedRiotId.normalizedRiotId(),
				accountResponse.gameName(),
				accountResponse.tagLine(),
				normalizedPlatform,
				accountResponse.puuid());
		account.setEnabled(true);
		return playerRiotAccountRepository.saveAndFlush(account);
	}

	// 백오피스 수동 수정 직후 단일 선수 즉시 동기화(실존 검증 겸용).
	// KR 주계정이 없으면 검증할 게 없어 조용히 통과. 존재하지 않는 Riot ID면 RiotApiException(404) 전파.
	public void syncPlayerAccountNow(Player player) {
		riotApiClient.assertConfigured();
		extractPrimaryAccount(player).ifPresent(candidate -> syncSinglePlayerAccount(player, candidate));
	}

	// KR 계정을 최우선으로 고르되, 없으면 첫 유효 지역 계정(해외 선수 EUW/NA 등)을 그 플랫폼으로 추적한다.
	private Optional<PrimaryAccountCandidate> extractPrimaryAccount(Player player) {
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

			String fallbackRegion = null;
			String fallbackRiotId = null;
			for (Map<String, Object> account : accounts) {
				String region = toText(account.get("region"));
				String riotId = toText(account.get("riotId"));
				if (riotId == null) {
					continue;
				}
				if (KR_PLATFORM.equalsIgnoreCase(region)) {
					return Optional.of(new PrimaryAccountCandidate(KR_PLATFORM, riotId));
				}
				if (fallbackRiotId == null) {
					fallbackRegion = region;
					fallbackRiotId = riotId;
				}
			}

			if (fallbackRiotId != null) {
				String platform = RiotPlatform.toPlatform(fallbackRegion);
				log.info("No KR account; tracking player={} on platform={}", player.getName(), platform);
				return Optional.of(new PrimaryAccountCandidate(platform, fallbackRiotId));
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

	private record PrimaryAccountCandidate(String platform, String riotId) {
	}
}
