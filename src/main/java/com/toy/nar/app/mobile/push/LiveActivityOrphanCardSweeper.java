package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 끝난 경기의 살아있는 Live Activity 카드를 주기적으로 닫는다.
 *
 * <p>카드 종료는 라이브 폴링 이벤트에 편승한다 — 세트 종료를 관측한 순간의 DB 스코어로
 * 매치 종료를 판정해 end 를 쏜다. 그래서 이벤트 기반의 빈틈이 그대로 카드 고착이 된다:
 * 스코어가 stale 제거(3분) 후에 도착하거나, discovery stale 폴백 경로라 복구 루프가 없거나,
 * bestOf 미상이라 종료 판정이 영영 false 거나, 재기동으로 in-memory 상태가 날아가거나.
 * 어느 경우든 카드는 iOS 한도(8시간)까지 "다음 세트 준비 중"으로 잠금화면에 남는다.</p>
 *
 * <p>이 스윕은 발송 경로가 아니라 DB 상태를 본다 — 살아있는 카드의 매치가 completed 면
 * 매치 종료를 보낸다. 위 빈틈 전부와, 앞으로 생길 발송 경로의 빈틈까지 한 번에 덮는다.
 * 서버가 아는 카드(update 토큰이 등록된 카드)만 닫을 수 있다는 한계는 그대로다 —
 * 토큰 없는 카드는 앱의 endAll 정리 경로만 남는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveActivityOrphanCardSweeper {

	private final LiveActivityTokenRepository tokenRepository;
	private final LeagueMatchRepository leagueMatchRepository;
	private final LiveActivityPushService liveActivityPushService;

	@Qualifier("applicationTaskExecutor")
	private final Executor applicationTaskExecutor;

	@Scheduled(fixedDelayString = "${apns.orphan-sweep-interval-ms:300000}")
	public void sweep() {
		if (!liveActivityPushService.isEnabled()) {
			return;
		}
		List<String> matchIds;
		try {
			matchIds = tokenRepository.findDistinctActiveMatchIds();
		} catch (Exception e) {
			log.warn("[live-activity] 스윕 대상 조회 실패: {}", e.getMessage());
			return;
		}
		if (matchIds.isEmpty()) {
			return;
		}
		for (LeagueMatch match : leagueMatchRepository.findAllById(matchIds)) {
			if (!"completed".equalsIgnoreCase(match.getState())) {
				continue;
			}
			int blue = match.getBlueScore() == null ? 0 : match.getBlueScore();
			int red = match.getRedScore() == null ? 0 : match.getRedScore();
			// 세트 번호는 진행도 워터마크(progressKey) 비교에만 쓰인다. 스코어 합이 곧 치른 세트
			// 수이고, 완료 매치의 스코어는 이미 확정이라 폴링이 봤을 어떤 세트보다 뒤거나 같다.
			int setNumber = Math.max(1, blue + red);
			String winner = blue == red ? null
					: blue > red ? match.getBlueTeamCode() : match.getRedTeamCode();
			log.info("[live-activity] 스윕 — 끝난 매치의 카드 정리 matchId={} score={}:{}",
					match.getId(), blue, red);
			// APNs 팬아웃을 스케줄러 스레드에서 돌리면 다른 잡의 슬롯을 막는다
			// (5슬롯 공유 — 2026-07-29 폴링 스레드가 발송 락 대기 50초에 걸린 실사고 참고).
			applicationTaskExecutor.execute(() -> liveActivityPushService.notifySetEnd(
					match.getId(), setNumber, blue, red, true, winner));
		}
	}
}
