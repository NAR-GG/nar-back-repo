package com.toy.nar.app.player;

import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.monitor.SchedulerAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LckRosterDiffScheduler {

	private final LckRosterDiffService lckRosterDiffService;
	private final NotificationService notificationService;
	private final SchedulerAlertService schedulerAlertService;

	// LCK 로스터 대조: 매일 새벽 5시 45분(KST). 선수 프로필 동기화(05:30) 직후.
	@Scheduled(cron = "${player.roster-diff.cron:0 45 5 * * *}", zone = "Asia/Seoul")
	public void detectRosterDiff() {
		long startTime = System.currentTimeMillis();
		try {
			List<LckRosterDiffService.RosterDiff> diffs = lckRosterDiffService.detect();
			notificationService.sendLckRosterDiffNotification(diffs.stream()
					.map(d -> String.format("%-12s %s -> %s", d.playerName(), d.currentTeamCode(), d.rosterTeamCode()))
					.toList());

			schedulerAlertService.recordSuccess(
					"LCK_ROSTER_DIFF",
					"LCK 로스터 대조 (불일치 " + diffs.size() + "건)",
					System.currentTimeMillis() - startTime);
			log.info("LCK roster diff completed: {} mismatch(es)", diffs.size());
		} catch (Exception e) {
			log.error("LCK roster diff failed", e);
			schedulerAlertService.recordFailure(
					"LCK_ROSTER_DIFF",
					"LCK 로스터 대조",
					e,
					"getTeams 로스터와 current_team_id 대조 중 오류");
		}
	}
}
