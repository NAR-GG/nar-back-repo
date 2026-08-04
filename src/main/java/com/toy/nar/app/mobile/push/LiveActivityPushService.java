package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * iOS Live Activity(잠금화면 실시간 경기 카드)를 서버가 직접 갱신한다.
 *
 * <p>앱이 30초 폴링으로 카드를 갱신하던 구조는 포그라운드에서만 동작했다 — iOS 가 백그라운드에서
 * 타이머를 멈추기 때문이다. 잠금화면 카드를 보는 시나리오가 곧 백그라운드라, 실제로는
 * "앱을 켜 놓고 볼 때만 맞는 위젯"이었다. APNs 로 직접 쏘면 앱 프로세스와 무관하게 갱신된다.</p>
 *
 * <p>Android 는 여기 오지 않는다. 진행 중 알림 기반이라 기존 FCM data 메시지
 * ({@link TeamLiveEventPushService} 가 이미 type/matchId/setNumber/bestOf/스코어를 싣는다)로
 * 앱을 깨워 알림을 다시 그리면 되고, 백엔드에 추가할 것이 없다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveActivityPushService {

	/** 클라이언트 {@code LiveMatchPhase.wireValue} 와 일치해야 한다. */
	private static final String PHASE_PLAYING = "playing";
	private static final String PHASE_SET_ENDED = "setEnded";
	private static final String PHASE_MATCH_ENDED = "matchEnded";

	/** 경기 종료 카드를 남겨 두는 시간. 앱의 자동 dismiss 타이머와 같은 값. */
	private static final Duration MATCH_END_DISMISS_AFTER = Duration.ofMinutes(30);

	private final LiveActivityTokenRepository tokenRepository;
	private final ApnsLiveActivityClient apnsClient;

	public boolean isEnabled() {
		return apnsClient.isAvailable();
	}

	/** 세트 시작 — 카드를 진행 중으로 바꾼다. */
	public void notifySetStart(String matchId, int setNumber, Integer blueScore, Integer redScore) {
		fanOut(matchId, contentState(PHASE_PLAYING, setNumber, blueScore, redScore, "", null), false);
	}

	/**
	 * 세트 종료 — 매치가 끝났으면 카드를 종료 상태로 바꾸고 시스템이 내리도록 예약한다.
	 *
	 * @param winnerTeamCode 매치 종료 시 승리 팀 코드. 진행 중이면 null.
	 */
	public void notifySetEnd(
			String matchId,
			int setNumber,
			Integer blueScore,
			Integer redScore,
			boolean matchEnded,
			String winnerTeamCode) {
		String phase = matchEnded ? PHASE_MATCH_ENDED : PHASE_SET_ENDED;
		String label = matchEnded ? "경기 종료" : "다음 세트 준비 중";
		Map<String, Object> state = contentState(
				phase, setNumber, blueScore, redScore, label, matchEnded ? winnerTeamCode : null);
		fanOut(matchId, state, matchEnded);
	}

	/**
	 * {@code MatchLiveAttributes.ContentState} 와 필드명·타입이 정확히 일치해야 한다.
	 * 모르는 값은 키를 넣지 않는다 — Swift 쪽이 Optional 이라 없으면 nil 로 읽힌다.
	 */
	private Map<String, Object> contentState(
			String phase,
			int setNumber,
			Integer blueScore,
			Integer redScore,
			String statusLabel,
			String winnerTeamCode) {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("phase", phase);
		state.put("setNumber", setNumber);
		// 앱은 세트 승자를 세어 스코어를 만들지만(set_winners 가 '?' 면 누락된다), 서버는
		// 매치 스코어를 그대로 싣는다. SET_END 푸시와 같은 값이라 알림과 카드가 어긋나지 않는다.
		state.put("scoreA", blueScore == null ? 0 : blueScore);
		state.put("scoreB", redScore == null ? 0 : redScore);
		state.put("statusLabel", statusLabel);
		if (winnerTeamCode != null && !winnerTeamCode.isBlank()) {
			state.put("winnerTeamCode", winnerTeamCode);
		}
		return state;
	}

	/**
	 * 이 매치의 살아있는 카드 전부에 보낸다.
	 *
	 * <p>발송 실패가 라이브 알림 흐름을 깨면 안 되므로 예외를 흡수한다. 죽은 토큰(410)만
	 * 비활성화하고, 일시 실패는 다음 이벤트에 자연히 재시도된다.</p>
	 */
	private void fanOut(String matchId, Map<String, Object> contentState, boolean end) {
		if (!isEnabled() || matchId == null || matchId.isBlank()) {
			return;
		}
		List<String> tokens;
		try {
			tokens = tokenRepository.findActivePushTokensByMatchId(matchId);
		} catch (Exception e) {
			log.warn("Live Activity 토큰 조회 실패 matchId={}: {}", matchId, e.getMessage());
			return;
		}
		if (tokens.isEmpty()) {
			return;
		}

		// 토큰마다 동기 발송하면 왕복이 직렬로 쌓여, 카드가 많은 경기에서 마지막 사람은
		// 세트가 끝난 뒤에야 카드가 갱신된다(FCM 쪽 실사고와 같은 모양 — ApnsLiveActivityClient 참고).
		// HTTP/2 다중화를 살리려면 전부 띄운 뒤 한 번에 기다려야 한다.
		Map<String, CompletableFuture<Boolean>> inFlight = new LinkedHashMap<>();
		for (String token : tokens) {
			inFlight.put(token, end
					? apnsClient.sendEndAsync(token, contentState, MATCH_END_DISMISS_AFTER)
					: apnsClient.sendUpdateAsync(token, contentState));
		}

		List<String> deadTokens = new ArrayList<>();
		inFlight.forEach((token, future) -> {
			// sendAsync 가 예외를 이미 흡수하지만, join 단계의 사고까지 발송 흐름을 깨지 않게 한 번 더 막는다.
			boolean alive;
			try {
				alive = future.join();
			} catch (Exception e) {
				log.warn("APNs 발송 결과 수집 실패 matchId={}: {}", matchId, e.getMessage());
				alive = true;
			}
			if (!alive) {
				deadTokens.add(token);
			}
		});
		log.info("[live-activity] matchId={} end={} 발송 {}건, 죽은 토큰 {}건",
				matchId, end, tokens.size(), deadTokens.size());

		// 매치가 끝났으면 이 카드들은 더 갱신되지 않는다 — 토큰을 함께 정리한다.
		List<String> toDeactivate = end ? tokens : deadTokens;
		if (toDeactivate.isEmpty()) {
			return;
		}
		try {
			tokenRepository.deactivateByPushTokenIn(toDeactivate);
		} catch (Exception e) {
			log.warn("Live Activity 토큰 비활성화 실패 matchId={}: {}", matchId, e.getMessage());
		}
	}
}
