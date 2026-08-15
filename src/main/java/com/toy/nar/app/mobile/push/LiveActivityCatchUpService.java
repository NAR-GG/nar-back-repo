package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.live.ActiveLiveGame;
import com.toy.nar.app.lolesports.live.LiveStateStore;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveActivityCatchUpService {

	private final LiveStateStore liveStateStore;
	private final LeagueMatchRepository leagueMatchRepository;
	private final LiveActivityPushService pushService;

	@Qualifier("applicationTaskExecutor")
	private final Executor applicationTaskExecutor;

	/** 경기 단위 구독 직후. 그 경기의 세트가 지금 진행 중이면 카드를 띄운다. */
	public void catchUpMatch(Long memberId, String matchId) {
		if (!ready(memberId) || matchId == null || matchId.isBlank()) {
			return;
		}
		liveGameOf(matchId).ifPresent(game -> leagueMatchRepository.findById(matchId)
				.ifPresent(match -> submit(memberId, match, game)));
	}

	/** 팀 구독(또는 세트 시작 알림 켜기) 직후. 그 팀의 진행 중인 경기 카드를 띄운다. */
	public void catchUpTeam(Long memberId, String teamCode) {
		if (!ready(memberId) || teamCode == null || teamCode.isBlank()) {
			return;
		}
		liveGames().forEach(game -> leagueMatchRepository.findById(game.matchId())
				.filter(match -> playsTeam(match, teamCode))
				.ifPresent(match -> submit(memberId, match, game)));
	}

	private boolean ready(Long memberId) {
		return memberId != null && pushService.isEnabled();
	}

	/** 지금 프레임이 들어오는(=종료 확정 전) 라이브 게임들. */
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
	private void submit(Long memberId, LeagueMatch match, ActiveLiveGame game) {
		int setNumber = setNumberOf(game, match);
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
	 * <p>ponytail: 업스트림 메타데이터가 없으면 확정 스코어 합 + 1 로 추정한다. 리메이크나
	 * 스코어 반영 지연이면 한 세트 어긋날 수 있지만, 다음 세트 시작 푸시가 바로잡는다.</p>
	 */
	private int setNumberOf(ActiveLiveGame game, LeagueMatch match) {
		if (game.setNumber() != null && game.setNumber() > 0) {
			return game.setNumber();
		}
		int blue = match.getBlueScore() == null ? 0 : match.getBlueScore();
		int red = match.getRedScore() == null ? 0 : match.getRedScore();
		return blue + red + 1;
	}
}
