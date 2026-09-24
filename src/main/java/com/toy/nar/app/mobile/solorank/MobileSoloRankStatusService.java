package com.toy.nar.app.mobile.solorank;

import com.toy.nar.app.mobile.solorank.dto.SoloRankStatusResponse;
import com.toy.nar.app.mobile.subscription.MobilePlayerSubscriptionService;
import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionResponse;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus;
import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;
import com.toy.nar.domain.participant.repository.PlayerSoloRankGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 홈 "구독 선수 솔랭" 카드. 이미 추적 중인 선수의 상태를 읽어 나눠 줄 뿐이라 구독이 늘어도
 * Riot 호출은 늘지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileSoloRankStatusService {

	/** 모니터가 이보다 오래 계정을 못 봤으면 IN_RANKED_SOLO 가 굳은 것으로 보고 라이브에서 뺀다. */
	static final Duration LIVE_FRESHNESS = Duration.ofMinutes(15);
	/** 끝난 게임을 돌려주는 범위. 앱이 이 안에서 노출 기간(spec 미결: 6시간 안)을 정한다. */
	static final Duration FINISHED_WINDOW = Duration.ofHours(24);
	/** 감지는 종료보다 앞선다(게임 길이 + 폴링 지연). 인덱스 범위용으로 넉넉히. */
	static final Duration DETECTION_SLACK = Duration.ofHours(3);

	private final MobilePlayerSubscriptionService subscriptionService;
	private final PlayerSoloRankGameRepository gameRepository;

	public SoloRankStatusResponse getStatus(Long memberId) {
		Map<Long, PlayerSubscriptionResponse> players = subscriptionService.getSubscriptions(memberId).stream()
				.collect(Collectors.toMap(PlayerSubscriptionResponse::playerId, Function.identity(),
						(left, right) -> left, LinkedHashMap::new));
		if (players.isEmpty()) {
			return new SoloRankStatusResponse(List.of(), List.of());
		}
		LocalDateTime now = LocalDateTime.now();

		List<SoloRankStatusResponse.LivePlayer> live = gameRepository
				.findLive(players.keySet(), PlayerRiotAccountLiveStatus.IN_RANKED_SOLO, now.minus(LIVE_FRESHNESS))
				.stream()
				.map(game -> toLive(game, players.get(game.getPlayer().getId())))
				.sorted(Comparator.comparing(SoloRankStatusResponse.LivePlayer::startedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		LocalDateTime endedSince = now.minus(FINISHED_WINDOW);
		Map<Long, PlayerSoloRankGame> latestFinished = new LinkedHashMap<>();
		// 최근 종료순이라 선수마다 처음 만나는 게 마지막 판이다.
		gameRepository.findFinishedSince(players.keySet(), endedSince.minus(DETECTION_SLACK), endedSince)
				.forEach(game -> latestFinished.putIfAbsent(game.getPlayer().getId(), game));
		List<SoloRankStatusResponse.FinishedPlayer> finished = latestFinished.values().stream()
				.map(game -> toFinished(game, players.get(game.getPlayer().getId())))
				.toList();

		return new SoloRankStatusResponse(live, finished);
	}

	private static SoloRankStatusResponse.LivePlayer toLive(PlayerSoloRankGame game, PlayerSubscriptionResponse player) {
		Champion champion = game.getChampion();
		LocalDateTime startedAt = game.getGameStartedAt() != null ? game.getGameStartedAt() : game.getDetectedAt();
		return new SoloRankStatusResponse.LivePlayer(
				player.playerId(),
				player.playerName(),
				player.playerImageUrl(),
				player.teamCode(),
				champion == null ? null : champion.getChampionNameKr(),
				champion == null ? null : champion.getImageUrl(),
				offset(startedAt));
	}

	private static SoloRankStatusResponse.FinishedPlayer toFinished(PlayerSoloRankGame game,
			PlayerSubscriptionResponse player) {
		Champion champion = game.getChampion();
		return new SoloRankStatusResponse.FinishedPlayer(
				player.playerId(),
				player.playerName(),
				player.playerImageUrl(),
				player.teamCode(),
				champion == null ? null : champion.getChampionNameKr(),
				champion == null ? null : champion.getImageUrl(),
				game.getWin(),
				game.getKills(),
				game.getDeaths(),
				game.getAssists(),
				game.getDurationSeconds(),
				offset(game.getGameEndedAt()));
	}

	/** 이 테이블의 시각은 JVM 시간대 벽시계다. 오프셋을 붙여 내보내야 앱이 로컬 시각으로 오해하지 않는다. */
	private static OffsetDateTime offset(LocalDateTime value) {
		return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
	}
}
