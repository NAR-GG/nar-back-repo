package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.live.ActiveLiveGame;
import com.toy.nar.app.lolesports.live.LiveStateStore;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * 진행 중인 경기를 구독한 직후, 그 회원의 잠금화면 카드를 따라잡아 띄운다.
 *
 * <p>카드 생성(push-to-start)은 세트 첫 프레임을 관측한 순간 1회만 돈다
 * ({@code LivePollingScheduler.startNotifiedGameIds} 게이트). 그래서 세트 진행 중에 구독하면
 * 다음 세트 시작까지 카드가 없고, 마지막 세트(또는 bo1)였다면 경기 내내 없다.</p>
 *
 * <p>구독은 사용자가 명시적으로 누른 1회 이벤트라 그 시점에만 한 발 쏜다. 폴링마다 채우는 방식은
 * 두 가지로 새는데, 지금은 앱이 카드 해제({@code DELETE /api/mobile/me/live-activities})를
 * 호출하지 않아 우연히 가려져 있을 뿐이다: (1) 앱이 해제를 구현하면 사용자가 지운 카드를 매 tick
 * 되살린다, (2) 카드는 떴지만 갱신 토큰 등록에 실패한 회원에게 카드를 계속 쌓는다.</p>
 *
 * <p>세트 사이(진행 중인 세트가 없는 휴식 구간)에는 띄우지 않는다 — 곧 오는 다음 세트 시작
 * push-to-start 가 정상 경로로 커버한다.</p>
 *
 * <p><b>라이브 여부는 인메모리와 DB 를 함께 본다.</b> 이 서비스는 구독 API 를 처리하는
 * <b>웹 파드</b>에서 돌고, {@code LiveStateStore} 는 인메모리라 폴링이 도는 스케줄러 파드에만
 * 채워진다. #442 로 파드를 뗀 뒤 웹 파드의 store 는 영구히 비어서, 인메모리만 보면 따라잡기가
 * <b>항상 no-op</b> 이었다(실측 2026-08-23 20:17 NS vs BFX 2세트 중 재구독 — 발송 0건, 로그 0줄).
 * DB 는 두 파드가 공유하므로 최신 프레임 신선도로 보강한다. #466 이 같은 원인으로 세트 상태를
 * 고친 것과 같은 방식이다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveActivityCatchUpService {

	/**
	 * 세트가 돌고 있다고 볼 최신 프레임 신선도. {@code LiveStateStore} 의 stale 제거(3분)와
	 * 같은 값으로 둬서 두 경로가 같은 리듬으로 움직인다(#466 의 판정과도 같은 값).
	 */
	private static final Duration LIVE_FRAME_FRESHNESS = Duration.ofMinutes(3);

	private final LiveStateStore liveStateStore;
	private final LiveGameMinuteSnapshotRepository minuteSnapshotRepository;
	private final LeagueMatchRepository leagueMatchRepository;
	private final LiveActivityPushService pushService;

	@Qualifier("applicationTaskExecutor")
	private final Executor applicationTaskExecutor;

	/** 경기 단위 구독 직후. 그 경기의 세트가 지금 진행 중이면 카드를 띄운다. */
	public void catchUpMatch(Long memberId, String matchId) {
		if (!ready(memberId) || matchId == null || matchId.isBlank()) {
			return;
		}
		if (!isLive(matchId)) {
			// 무동작을 조용히 넘기지 않는다 — 파드 분리로 이 경로가 통째로 죽어 있던 것을
			// 하루 동안 아무도 몰랐던 이유가 "대상 없음"과 "고장"이 로그에서 구분되지 않아서다.
			log.info("[live-activity] 구독 직후 따라잡기 건너뜀 — 진행 중인 세트 없음 matchId={} memberId={}",
					matchId, memberId);
			return;
		}
		leagueMatchRepository.findById(matchId)
				.ifPresent(match -> submit(memberId, match, setNumberOf(matchId, match)));
	}

	/** 팀 구독(또는 세트 시작 알림 켜기) 직후. 그 팀의 진행 중인 경기 카드를 띄운다. */
	public void catchUpTeam(Long memberId, String teamCode) {
		if (!ready(memberId) || teamCode == null || teamCode.isBlank()) {
			return;
		}
		for (String matchId : liveMatchIdCandidates()) {
			if (!isLive(matchId)) {
				continue;
			}
			leagueMatchRepository.findById(matchId)
					.filter(match -> playsTeam(match, teamCode))
					.ifPresent(match -> submit(memberId, match, setNumberOf(matchId, match)));
		}
	}

	private boolean ready(Long memberId) {
		return memberId != null && pushService.isEnabled();
	}

	/**
	 * 이 매치의 세트가 지금 돌고 있는지. 인메모리(스케줄러 파드) 또는 최신 프레임(양 파드 공유).
	 *
	 * <p>DB 경로에도 {@code isFinished} 를 거는 이유는 #466 과 같다 — 스케줄러 파드에서는 종료
	 * 확정된 세트가 stale 제거 전까지 신선한 프레임을 갖고 있어서, 안 걸면 끝난 세트가 살아난다.</p>
	 *
	 * <p>ponytail: 신선도 창(3분)만큼은 세트가 끝난 뒤에도 라이브로 보인다. 그 창에 구독하면 카드가
	 * 한 장 뜨고 곧 매치 종료 푸시나 orphan 스윕이 닫는다. 영구 no-op 보다는 낫다.</p>
	 */
	private boolean isLive(String matchId) {
		if (liveGameOf(matchId).isPresent()) {
			return true;
		}
		return minuteSnapshotRepository
				.findFreshGameIdsByMatchId(matchId, freshSince()).stream()
				.filter(gameId -> gameId != null && !gameId.isBlank())
				.anyMatch(gameId -> !liveStateStore.isFinished(gameId));
	}

	/**
	 * 지금 라이브일 수 있는 매치 id 후보. 확정 판정은 {@link #isLive} 가 매치별로 다시 한다.
	 *
	 * <p>팀 구독 경로는 대상 매치를 모르는 상태로 시작하므로 후보를 먼저 모아야 한다.
	 * 인메모리와 DB 를 합집합으로 둬서 어느 파드에서 돌아도 같은 결과가 나온다.</p>
	 */
	private Set<String> liveMatchIdCandidates() {
		Set<String> matchIds = new LinkedHashSet<>();
		liveGames().map(ActiveLiveGame::matchId).forEach(matchIds::add);
		minuteSnapshotRepository.findFreshMatchIds(freshSince()).stream()
				.filter(matchId -> matchId != null && !matchId.isBlank())
				.forEach(matchIds::add);
		return matchIds;
	}

	/** {@code frameTimestampUtc} 가 UTC 라 기준 시각도 UTC 로 만든다. KST 로 물으면 9시간 미래가 기준이 된다. */
	private LocalDateTime freshSince() {
		return LocalDateTime.now(ZoneOffset.UTC).minus(LIVE_FRAME_FRESHNESS);
	}

	/** 지금 프레임이 들어오는(=종료 확정 전) 라이브 게임들. 스케줄러 파드에서만 채워져 있다. */
	private java.util.stream.Stream<ActiveLiveGame> liveGames() {
		return liveStateStore.getActiveGames().values().stream()
				.filter(game -> game.matchId() != null && !game.matchId().isBlank())
				.filter(game -> !liveStateStore.isFinished(game.gameId()));
	}

	private Optional<ActiveLiveGame> liveGameOf(String matchId) {
		return liveGames().filter(game -> matchId.equals(game.matchId())).findFirst();
	}

	private boolean playsTeam(LeagueMatch match, String teamCode) {
		return teamCode.equalsIgnoreCase(match.getBlueTeamCode())
				|| teamCode.equalsIgnoreCase(match.getRedTeamCode());
	}

	/**
	 * APNs 왕복을 요청 스레드에서 기다리지 않는다 — 구독 응답이 발송에 묶이면 안 된다.
	 * 발송 실패가 구독 자체를 깨면 안 되므로 예외도 흡수한다.
	 */
	private void submit(Long memberId, LeagueMatch match, int setNumber) {
		applicationTaskExecutor.execute(() -> {
			try {
				log.info("[live-activity] 구독 직후 카드 생성 matchId={} memberId={} set={}",
						match.getId(), memberId, setNumber);
				// 세트마다 진영이 스왑되므로 팀 표기는 매치 기준을 쓴다(set-start 경로와 동일).
				pushService.startCardForMember(
						match.getId(),
						memberId,
						setNumber,
						match.getBlueScore(),
						match.getRedScore(),
						new LiveActivityPushService.MatchCardAttributes(
								match.getId(),
								match.getBlueTeamName(), match.getBlueTeamCode(),
								match.getRedTeamName(), match.getRedTeamCode(),
								match.getLeagueName()));
			} catch (Exception e) {
				log.warn("[live-activity] 구독 직후 카드 생성 실패 matchId={} memberId={}: {}",
						match.getId(), memberId, e.getMessage());
			}
		});
	}

	/**
	 * 카드에 그릴 세트 번호. 라이브 게임이 아는 값이 먼저다.
	 *
	 * <p>웹 파드에는 인메모리 게임이 없으므로 대개 아래 추정으로 내려간다.</p>
	 *
	 * <p>ponytail: 업스트림 메타데이터가 없으면 확정 스코어 합 + 1 로 추정한다. 리메이크나
	 * 스코어 반영 지연이면 한 세트 어긋날 수 있지만, 다음 세트 시작 푸시가 바로잡는다.</p>
	 */
	private int setNumberOf(String matchId, LeagueMatch match) {
		Integer fromLiveGame = liveGameOf(matchId).map(ActiveLiveGame::setNumber).orElse(null);
		if (fromLiveGame != null && fromLiveGame > 0) {
			return fromLiveGame;
		}
		int blue = match.getBlueScore() == null ? 0 : match.getBlueScore();
		int red = match.getRedScore() == null ? 0 : match.getRedScore();
		return blue + red + 1;
	}
}
