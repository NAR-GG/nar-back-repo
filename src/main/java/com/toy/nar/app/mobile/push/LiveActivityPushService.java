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
import java.util.concurrent.ConcurrentHashMap;

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

	/**
	 * 매치별로 마지막에 카드에 반영한 진행도. 뒤처진 이벤트를 걸러내는 워터마크다.
	 *
	 * <p>업스트림이 이미 끝난 게임 id 를 {@code liveGameIds} 에 계속 실어 보내면 디스커버리가
	 * 그 게임을 다시 추적하고, stale 제거로 dedup 집합이 비어 있어 세트 종료가 재발화한다
	 * (2026-07-31 LCK Gen.G vs T1 실측: 1세트 종료가 2세트 진행 중에 5회 추가 발화, 6~7분 주기).
	 * FCM 은 발송 이력 테이블이 막아 주지만 카드에는 그런 장치가 없어, 2세트를 하는 내내 카드가
	 * "SET 1 종료 / 다음 세트 준비 중" 으로 덮이고, 더 나쁘게는 낡은 세트 종료가 그 시점의
	 * 스코어로 매치 종료로 판정돼 카드가 경기 도중 닫힐 수 있다.</p>
	 *
	 * <p>발화 경로(프레임 판정·디스커버리 폴백)나 재기동과 무관하게 막으려면 여기서 걸러야 한다.
	 * ponytail: 매치 종료 시 지우므로 진행 중 매치 수만큼만 남는다. 종료 이벤트를 못 받은
	 * 매치(bestOf 미상 등)의 항목은 남지만 하루 수십 건이라 무해하다.</p>
	 */
	private final Map<String, Long> lastProgressByMatch = new ConcurrentHashMap<>();

	public boolean isEnabled() {
		return apnsClient.isAvailable();
	}

	/** 세트 시작 — 카드를 진행 중으로 바꾼다. */
	public void notifySetStart(String matchId, int setNumber, Integer blueScore, Integer redScore) {
		if (!accept(matchId, setNumber, PHASE_PLAYING)) {
			return;
		}
		fanOut(matchId, contentState(PHASE_PLAYING, setNumber, blueScore, redScore, "", null), false);
	}

	/**
	 * 카드 진행도가 뒤로 가는 이벤트를 버린다.
	 *
	 * <p>정상 순서는 (1,playing) → (1,setEnded) → (2,playing) → ... 로 단조 증가한다.
	 * 같은 값이 다시 오는 것은 통과시킨다 — 같은 상태를 다시 그리는 것뿐이라 무해하고,
	 * 그 사이 스코어가 갱신됐을 수 있다.</p>
	 */
	private boolean accept(String matchId, int setNumber, String phase) {
		if (matchId == null || matchId.isBlank()) {
			return false;
		}
		if (setNumber < 1) {
			// 세트 번호를 모르면 카드에 그릴 수도, 순서를 따질 수도 없다.
			log.debug("[live-activity] 세트 번호 미상이라 카드 갱신을 건너뛴다. matchId={}", matchId);
			return false;
		}
		long key = progressKey(setNumber, phase);
		// compute 로 검사와 갱신을 한 번에 한다 — 매치가 같은 이벤트가 동시에 들어올 수 있다.
		boolean[] accepted = { false };
		lastProgressByMatch.compute(matchId, (id, previous) -> {
			if (previous != null && key < previous) {
				return previous;
			}
			accepted[0] = true;
			return key;
		});
		if (!accepted[0]) {
			log.info("[live-activity] 뒤처진 이벤트 무시 matchId={} set={} phase={} (마지막={})",
					matchId, setNumber, phase, lastProgressByMatch.get(matchId));
		}
		return accepted[0];
	}

	/** 세트 번호가 먼저, 같은 세트면 playing < setEnded < matchEnded 순. */
	private static long progressKey(int setNumber, String phase) {
		int phaseRank = switch (phase) {
			case PHASE_SET_ENDED -> 1;
			case PHASE_MATCH_ENDED -> 2;
			default -> 0;
		};
		return setNumber * 10L + phaseRank;
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
		if (!accept(matchId, setNumber, phase)) {
			return;
		}
		String label = matchEnded ? "경기 종료" : "다음 세트 준비 중";
		Map<String, Object> state = contentState(
				phase, setNumber, blueScore, redScore, label, matchEnded ? winnerTeamCode : null);
		fanOut(matchId, state, matchEnded);
		if (matchEnded) {
			// 카드가 닫혔으니 워터마크도 함께 정리한다. 이후 늦게 도착하는 이벤트는
			// 토큰이 이미 비활성이라 fanOut 에서 자연히 걸러진다.
			lastProgressByMatch.remove(matchId);
		}
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
		// 매치 단위 정리는 조건으로 지우는 편이 낫다. 토큰을 IN 절로 넘기면 카드 수만큼
		// 문자열이 실려 SQL 이 커지는데, "이 매치 전부"는 인덱스 한 번이면 되는 조건이다.
		try {
			if (end) {
				tokenRepository.deactivateAllByMatchId(matchId);
			} else if (!deadTokens.isEmpty()) {
				tokenRepository.deactivateByPushTokenIn(deadTokens);
			}
		} catch (Exception e) {
			log.warn("Live Activity 토큰 비활성화 실패 matchId={}: {}", matchId, e.getMessage());
		}
	}
}
