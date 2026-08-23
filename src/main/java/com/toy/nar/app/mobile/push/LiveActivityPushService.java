package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.LiveActivityCardDispatchRepository;
import com.toy.nar.domain.member.repository.LiveActivityStartTokenRepository;
import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>이 경로는 알림 잠자기({@code QuietAwarePushSender}) 대상이 아니다. APNs
 * push-to-start/update 는 {@code alert}·{@code sound} 없이 content-state 만 바꾸는
 * Live Activity 갱신이라, 애초에 소리나 배너가 나지 않는다 — 잠자기를 끼울 지점이 없다.</p>
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

	/** 앱의 ActivityAttributes 타입 이름. 다르면 APNs 는 200 을 주고 카드만 안 뜬다. */
	private static final String ATTRIBUTES_TYPE = "MatchLiveAttributes";

	/**
	 * 방금 발행한 카드를 "다시 발행해도 된다"고 보지 않는 시간.
	 *
	 * <p>따라잡기 경로는 발행 이력을 풀고 다시 발행한다(사용자가 카드를 지웠을 수 있으므로).
	 * 그런데 갱신 토큰은 발행 뒤 2~10초 있어야 올라오므로 그 창 안에서는
	 * {@link #closePreviousCard} 가 닫을 토큰을 못 찾아 앞 카드가 살아남는다 — 짧은 간격의 연속
	 * 구독 액션이 카드를 두 장 만든다(실측 2026-08-16 member 10, 17:07:37 / 17:07:39).
	 * 등록 지연 최대치의 3배로 잡는다.</p>
	 *
	 * <p>예전에는 같은 목적의 창이 Caffeine 인메모리였는데, #442 로 파드가 둘이 된 뒤 세트 시작
	 * (스케줄러)과 따라잡기(웹)가 서로 다른 JVM 이라 공유되지 않았다. DB 시계로 옮겨 고쳤다.</p>
	 */
	private static final Duration RECENT_DISPATCH_WINDOW = Duration.ofSeconds(30);

	private final LiveActivityTokenRepository tokenRepository;
	private final LiveActivityStartTokenRepository startTokenRepository;
	private final LiveActivityCardDispatchRepository cardDispatchRepository;
	private final ApnsLiveActivityClient apnsClient;

	/**
	 * push-to-start 별도 스위치. 카드를 "갱신"하는 것과 달리 사용자가 띄우지 않은 카드를
	 * 잠금화면에 만들어 내는 동작이라, APNs 전체를 켠 뒤에도 이것만 따로 끌 수 있게 둔다.
	 */
	@Value("${apns.push-to-start.enabled:false}")
	private boolean pushToStartEnabled;

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

	/**
	 * 매치 종료 카드를 이미 내보낸 매치. 발송자가 셋(프레임 편승·복구 재시도·스윕)이라
	 * "종료가 나간 뒤 늦은 setEnded 가 카드를 되돌리는" 역행을 막는 상태는 발송 지점인
	 * 여기서 공유해야 한다 — 스케줄러 안에 있으면 스윕 발송이 보이지 않는다.
	 */
	private final java.util.Set<String> matchEndPushedMatchIds =
			java.util.concurrent.ConcurrentHashMap.newKeySet();

	public boolean isEnabled() {
		return apnsClient.isAvailable();
	}

	/** 이 매치의 종료 카드가 이미 나갔는지. 늦은 setEnded 를 쏘기 전에 확인한다. */
	public boolean matchEndPushed(String matchId) {
		return matchEndPushedMatchIds.contains(matchId);
	}

	/** 종료 발송 선점. 처음 선점했으면 true — 동시 진입하는 발송 경로의 dedup 에 쓴다. */
	public boolean claimMatchEndPush(String matchId) {
		return matchEndPushedMatchIds.add(matchId);
	}

	/** 세트 시작 — 카드를 진행 중으로 바꾼다. */
	public void notifySetStart(String matchId, int setNumber, Integer blueScore, Integer redScore) {
		if (!accept(matchId, setNumber, PHASE_PLAYING)) {
			return;
		}
		fanOut(matchId, contentState(PHASE_PLAYING, setNumber, blueScore, redScore, "", null), false);
	}

	/**
	 * 구독자에게 카드를 새로 띄운다(push-to-start).
	 *
	 * <p>지금까지 카드는 앱이 실행돼야만 떴다. 잠금화면 카드를 보는 상황이 곧 앱이 안 떠 있는
	 * 상황이라, 정작 필요한 때에 카드가 없었다. 세트 시작 시점에 서버가 만들어 준다.</p>
	 *
	 * <p>{@code notifySetStart} 와 대상이 다르다. 그쪽은 이미 카드를 띄운 토큰에 보내고,
	 * 이쪽은 이 경기를 구독한 회원 중 아직 카드가 없는 사람에게 보낸다. 두 경로는 겹치지 않는다.</p>
	 *
	 * @param blueTeamId 팀 구독 매칭용 내부 팀 id. 해석 실패 시 null (경기 구독자만 대상이 된다)
	 */
	public void startCards(
			String matchId,
			int setNumber,
			Integer blueScore,
			Integer redScore,
			Long blueTeamId,
			Long redTeamId,
			MatchCardAttributes attributes) {
		if (!isEnabled() || !pushToStartEnabled || matchId == null || matchId.isBlank()) {
			return;
		}
		List<LiveActivityStartTokenRepository.StartTargetRow> targets;
		try {
			targets = startTokenRepository.findStartTargets(matchId, blueTeamId, redTeamId);
		} catch (Exception e) {
			log.warn("push-to-start 대상 조회 실패 matchId={}: {}", matchId, e.getMessage());
			return;
		}
		sendStarts(matchId, setNumber, blueScore, redScore, attributes, targets);
	}

	/**
	 * 회원 한 명에게만 카드를 새로 띄운다 — 진행 중인 경기를 구독한 직후의 따라잡기용.
	 *
	 * <p>{@link #startCards} 는 세트 첫 프레임 관측 때 1회만 돌기 때문에, 세트 진행 중에 구독한
	 * 사람은 다음 세트 시작까지(마지막 세트였다면 경기 내내) 카드가 없다. 그 구멍만 메운다.</p>
	 *
	 * <p>발행 전에 그 회원의 이전 카드를 닫는다. 사용자가 잠금화면에서 카드를 지워도 서버는
	 * 모르므로(앱에 해제 호출 경로가 없다) 갱신 토큰이 유령으로 남아 중복 방지 조건에 걸려
	 * 재신청해도 카드가 다시 뜨지 않았다 — 실측 2026-08-12 DK vs KT: 카드 삭제 뒤 구독
	 * 해제·재신청에도 발송 0건. 이전 카드가 실제로 살아있었다면 닫히므로 두 장이 되지 않는다.</p>
	 */
	public void startCardForMember(
			String matchId,
			Long memberId,
			int setNumber,
			Integer blueScore,
			Integer redScore,
			MatchCardAttributes attributes) {
		if (!isEnabled() || !pushToStartEnabled || memberId == null
				|| matchId == null || matchId.isBlank()) {
			return;
		}
		closePreviousCard(matchId, memberId, setNumber, blueScore, redScore);
		releaseDispatch(matchId, memberId);
		List<LiveActivityStartTokenRepository.StartTargetRow> targets;
		try {
			targets = startTokenRepository.findStartTargetsForMember(matchId, memberId);
		} catch (Exception e) {
			log.warn("push-to-start 대상 조회 실패 matchId={} memberId={}: {}",
					matchId, memberId, e.getMessage());
			return;
		}
		sendStarts(matchId, setNumber, blueScore, redScore, attributes, targets);
	}

	/**
	 * 이 회원의 이 경기 카드를 즉시 닫고 갱신 토큰을 비활성화한다. 카드가 이미 없으면
	 * APNs 가 200 을 주고 아무 일도 일어나지 않으므로(유령 토큰) 무해하다.
	 *
	 * <p>발송 성공 여부와 무관하게 토큰을 정리한다 — 유령 토큰이 남아 재발행을 계속 막는 쪽이
	 * 카드가 잠깐 두 장 보이는 것보다 나쁘다. 정리는 새 카드 대상 조회보다 먼저 끝나야 한다
	 * (조회의 중복 방지 조건이 이 토큰을 보고 대상에서 제외한다).</p>
	 */
	private void closePreviousCard(
			String matchId, Long memberId, int setNumber, Integer blueScore, Integer redScore) {
		List<String> previousTokens;
		try {
			previousTokens = tokenRepository.findActivePushTokensByMemberIdAndMatchId(memberId, matchId);
		} catch (Exception e) {
			log.warn("이전 카드 토큰 조회 실패 matchId={} memberId={}: {}", matchId, memberId, e.getMessage());
			return;
		}
		if (previousTokens.isEmpty()) {
			return;
		}
		Map<String, Object> state = contentState(PHASE_PLAYING, setNumber, blueScore, redScore, "", null);
		for (String pushToken : previousTokens) {
			try {
				// dismissal-date 를 지금으로 둬 카드가 바로 사라지게 한다. 새 카드가 곧 뜨므로
				// 매치 종료(30분 유지)와 달리 남겨둘 이유가 없다.
				apnsClient.sendEndAsync(pushToken, state, Duration.ZERO).join();
			} catch (Exception e) {
				log.warn("이전 카드 닫기 실패 matchId={} memberId={}: {}", matchId, memberId, e.getMessage());
			}
		}
		try {
			tokenRepository.deactivateByPushTokenIn(previousTokens);
		} catch (Exception e) {
			log.warn("이전 카드 토큰 비활성화 실패 matchId={} memberId={}: {}", matchId, memberId, e.getMessage());
		}
		log.info("[live-activity] 재발행 위해 이전 카드 닫음 matchId={} memberId={} 토큰={}건",
				matchId, memberId, previousTokens.size());
	}

	/**
	 * 이 회원의 이 매치 발행 이력을 지워 카드를 다시 만들 수 있게 한다.
	 *
	 * <p>발행 선점이 매치 단위라, 이걸 풀지 않으면 사용자가 카드를 지우고 재구독해도 선점에 걸려
	 * 그 매치 내내 카드가 안 뜬다. 재발행 의사가 확실한 경로(구독 직후 따라잡기)에서만 부른다.</p>
	 *
	 * <p>{@link #closePreviousCard} 보다 <b>먼저</b> 부르지 않아도 된다. 순서 의존은 없다 —
	 * 대상 조회 전에만 끝나면 된다.</p>
	 */
	private void releaseDispatch(String matchId, Long memberId) {
		try {
			cardDispatchRepository.release(memberId, matchId, RECENT_DISPATCH_WINDOW);
		} catch (Exception e) {
			log.warn("카드 발행 이력 해제 실패 matchId={} memberId={}: {}", matchId, memberId, e.getMessage());
		}
	}

	/** 대상 산정만 다르고 발송·죽은 토큰 정리는 같다. */
	private void sendStarts(
			String matchId,
			int setNumber,
			Integer blueScore,
			Integer redScore,
			MatchCardAttributes attributes,
			List<LiveActivityStartTokenRepository.StartTargetRow> targets) {
		if (targets.isEmpty()) {
			return;
		}
		targets = claimDispatch(matchId, setNumber, targets);
		if (targets.isEmpty()) {
			return;
		}

		Map<String, Object> state = contentState(PHASE_PLAYING, setNumber, blueScore, redScore, "", null);
		// alert 는 iOS 가 start 를 받아들이는 필수 조건이다(ApnsLiveActivityClient.sendStartAsync 참고).
		// 문구는 SET_START FCM 배너와 겹치므로 최소한으로 — 시스템이 상황 따라 워치 등에만 쓴다.
		String alertTitle = attributes.teamAName() + " vs " + attributes.teamBName();
		String alertBody = setNumber + "세트 시작";
		// 발송 건수만 남기면 일시 실패가 성공으로 집계돼 로그가 거짓말을 한다
		// (2026-08-17: "발송 1228건" 중 228건이 APNs 동시 스트림 한도로 죽어 있었다).
		long failuresBefore = apnsClient.transientFailureCount();
		Map<String, CompletableFuture<Boolean>> inFlight = new LinkedHashMap<>();
		for (LiveActivityStartTokenRepository.StartTargetRow target : targets) {
			// 응원 팀 하트는 회원마다 달라 payload 를 회원별로 만든다.
			inFlight.put(target.getPushToken(), apnsClient.sendStartAsync(
					target.getPushToken(),
					ATTRIBUTES_TYPE,
					attributes.toPayload(target.getFavoriteTeamCode()),
					state,
					alertTitle,
					alertBody));
		}

		List<String> deadTokens = new ArrayList<>();
		inFlight.forEach((token, future) -> {
			boolean alive;
			try {
				alive = future.join();
			} catch (Exception e) {
				log.warn("push-to-start 결과 수집 실패 matchId={}: {}", matchId, e.getMessage());
				alive = true;
			}
			if (!alive) {
				deadTokens.add(token);
			}
		});
		log.info("[live-activity] push-to-start matchId={} set={} 발송 {}건, 일시 실패 {}건, 죽은 토큰 {}건",
				matchId, setNumber, targets.size(),
				apnsClient.transientFailureCount() - failuresBefore, deadTokens.size());

		// 죽은 토큰의 주인을 남긴다. 개수만 남기면 "카드가 안 뜬다" 는 제보가 왔을 때
		// 그 회원이 죽은 토큰 때문이었는지 확인할 방법이 없다(2026-08-17 실제로 겪었다).
		if (!deadTokens.isEmpty()) {
			targets.stream()
					.filter(t -> deadTokens.contains(t.getPushToken()))
					.forEach(t -> log.warn("[live-activity] 죽은 start token memberId={} token={}…",
							t.getMemberId(), t.getPushToken().substring(0, Math.min(12, t.getPushToken().length()))));
		}

		if (!deadTokens.isEmpty()) {
			try {
				startTokenRepository.deactivateByPushTokenIn(deadTokens);
			} catch (Exception e) {
				log.warn("push-to-start 토큰 비활성화 실패 matchId={}: {}", matchId, e.getMessage());
			}
		}
	}

	/**
	 * 아직 이 매치에 카드를 발행하지 않은 회원만 남긴다.
	 *
	 * <p>선점 이력이 DB 라 <b>세트가 바뀌어도, 파드가 바뀌어도, 재기동해도</b> 유지된다. 그래서
	 * 카드는 매치당 한 장이다 — 세트 전환은 갱신 토큰으로 반영하고 새로 만들지 않는다. 실측
	 * 2026-08-23 T1 vs HLE 에서 세트1 3,120건 뒤 세트2 에 2,702건이 또 나가 약 2,700명이 카드
	 * 두 장을 받았고, 갱신 토큰이 없는 옛 카드는 만들어진 시점 스코어(0:0)에 영구히 멈춰 있었다.</p>
	 *
	 * <p><b>선점은 회원 단위, 발송은 토큰 단위다.</b> 회원 단위로 선점하고 토큰 하나만 골라
	 * 보내면 안 된다 — 대상 조회에 정렬이 없어 "한 대"가 임의로 정해지고, 오래된 유령 토큰이
	 * 뽑히면 그 회원은 카드를 영영 못 받는다. 유령에게 보내지 않으니 410 도 못 받아
	 * {@link LiveActivityStartTokenRepository#deactivateByPushTokenIn 자동 정리}조차 안 돈다
	 * (실측 2026-08-20 member 621: 활성 토큰 19개가 쌓여 8/13 부터 카드가 한 번도 안 떴다).
	 * 그래서 선점된 회원의 토큰은 전부 보낸다 — 기기마다 한 장이 원래 의도한 동작이다.</p>
	 *
	 * <p>조회가 실패하면 선점을 건너뛰고 전부 보낸다. 카드가 한 장 더 뜨는 것보다 아무것도 못
	 * 받는 쪽이 나쁘다.</p>
	 */
	private List<LiveActivityStartTokenRepository.StartTargetRow> claimDispatch(
			String matchId,
			int setNumber,
			List<LiveActivityStartTokenRepository.StartTargetRow> targets) {
		List<Long> memberIds = targets.stream()
				.map(LiveActivityStartTokenRepository.StartTargetRow::getMemberId)
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();
		if (memberIds.isEmpty()) {
			return targets;
		}
		java.util.Set<Long> claimed;
		try {
			claimed = new java.util.HashSet<>(
					cardDispatchRepository.claimAll(memberIds, matchId, setNumber));
		} catch (Exception e) {
			log.warn("카드 발행 선점 실패 — 선점 없이 발송한다 matchId={}: {}", matchId, e.getMessage());
			return targets;
		}
		int skipped = memberIds.size() - claimed.size();
		if (skipped > 0) {
			log.info("[live-activity] 이미 카드를 발행한 회원 제외 matchId={} set={} 제외 {}명",
					matchId, setNumber, skipped);
		}
		return targets.stream()
				.filter(target -> target.getMemberId() != null && claimed.contains(target.getMemberId()))
				.toList();
	}

	/**
	 * 카드의 정적 속성. Swift {@code MatchLiveAttributes} 의 필드 구성과 정확히 맞아야 한다.
	 *
	 * <p>로고는 파일명만 넘긴다. 앱이 App Group 에 미리 캐싱해 둔 파일을 위젯이 읽는 구조라
	 * (확장은 렌더 시점에 네트워크를 못 쓴다), 파일이 없으면 로고 없이 그려진다.</p>
	 */
	public record MatchCardAttributes(
			String matchId,
			String teamAName,
			String teamACode,
			String teamBName,
			String teamBCode,
			String leagueName) {

		Map<String, Object> toPayload(String favoriteTeamCode) {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("matchId", nullToEmpty(matchId));
			payload.put("teamAName", nullToEmpty(teamAName));
			payload.put("teamACode", nullToEmpty(teamACode));
			payload.put("teamBName", nullToEmpty(teamBName));
			payload.put("teamBCode", nullToEmpty(teamBCode));
			payload.put("leagueName", nullToEmpty(leagueName));
			// 앱과 합의한 캐시 파일명 규칙: <팀코드>.png
			logoFile(teamACode).ifPresent(file -> payload.put("teamALogoFile", file));
			logoFile(teamBCode).ifPresent(file -> payload.put("teamBLogoFile", file));
			if (favoriteTeamCode != null && !favoriteTeamCode.isBlank()) {
				payload.put("favoriteTeamCode", favoriteTeamCode);
			}
			return payload;
		}

		private static java.util.Optional<String> logoFile(String teamCode) {
			return teamCode == null || teamCode.isBlank()
					? java.util.Optional.empty()
					: java.util.Optional.of(teamCode + ".png");
		}

		private static String nullToEmpty(String value) {
			return value == null ? "" : value;
		}
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
			claimMatchEndPush(matchId);
			// 카드가 닫혔으니 워터마크도 함께 정리한다. 이후 늦게 도착하는 이벤트는
			// 토큰이 이미 비활성이라 fanOut 에서 자연히 걸러진다.
			lastProgressByMatch.remove(matchId);
		}
	}

	/**
	 * 매치 종료 강제 발송 — 스윕 전용. 진행도 워터마크({@link #accept}) 검사를 건너뛴다.
	 *
	 * <p>워터마크는 발송 이벤트끼리의 순서를 지키는 장치인데, 스윕의 근거는 이벤트가 아니라
	 * DB 확정 상태(completed)다. completed 보다 미래의 이벤트는 없으므로 순서 검사가 지킬 게
	 * 없고, 오히려 방해가 된다 — 리메이크로 폴링 워터마크(gameIds 인덱스 기준 세트)가 스코어 합
	 * 기반 세트 추정보다 높으면 스윕 발송이 영구 기각돼, 스윕이 잡으려던 카드 고착을 스윕
	 * 자신이 재생산한다(5분마다 기각 반복).</p>
	 */
	public void forceMatchEnd(String matchId, int setNumber, Integer blueScore, Integer redScore,
			String winnerTeamCode) {
		if (matchId == null || matchId.isBlank()) {
			return;
		}
		Map<String, Object> state = contentState(
				PHASE_MATCH_ENDED, Math.max(1, setNumber), blueScore, redScore, "경기 종료", winnerTeamCode);
		fanOut(matchId, state, true);
		claimMatchEndPush(matchId);
		lastProgressByMatch.remove(matchId);
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
		long failuresBefore = apnsClient.transientFailureCount();
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
		log.info("[live-activity] matchId={} end={} 발송 {}건, 일시 실패 {}건, 죽은 토큰 {}건",
				matchId, end, tokens.size(),
				apnsClient.transientFailureCount() - failuresBefore, deadTokens.size());

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

		// 발행 이력도 매치가 끝나면 쓸모가 없다. 여기서 지우지 않으면 경기마다 구독자 수만큼
		// (실측 3천 행) 쌓여 무한히 자란다 — 알림함이 172만 행이 된 것과 같은 모양이 된다.
		if (end) {
			try {
				cardDispatchRepository.deleteAllByMatchId(matchId);
			} catch (Exception e) {
				log.warn("카드 발행 이력 정리 실패 matchId={}: {}", matchId, e.getMessage());
			}
		}
	}
}
