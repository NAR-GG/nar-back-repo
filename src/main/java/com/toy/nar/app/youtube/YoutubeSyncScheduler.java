package com.toy.nar.app.youtube;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeSyncScheduler {

	private final YoutubeSyncService youtubeSyncService;

	/**
	 * 매 1시간마다 최근 24시간 내 업로드된 영상의 댓글을 동기화합니다.
	 * Cron: 매 정각 0분 0초
	 */
	@Scheduled(cron = "0 0 * * * *")
	public void scheduleRecentCommentsSync() {
		log.info("### [Scheduler] 최근 댓글 동기화 시작 ###");
		try {
			youtubeSyncService.syncRecentComments();
		} catch (Exception e) {
			log.error("### [Scheduler] 최근 댓글 동기화 중 오류 발생 ###", e);
		}
	}

	/**
	 * 매 10분마다 최근 3시간 이내에 업로드된 영상의 통계를 갱신합니다.
	 * Cron: 10분 간격 (0, 10, 20, 30, 40, 50분)
	 */
	@Scheduled(cron = "0 0/10 * * * *")
	public void scheduleRecentThreeHoursVideosStatsSync() {
		log.info("### [Scheduler] 최근 3시간 영상 통계 갱신 시작 ###");
		try {
			// 현재 시간으로부터 3시간 전 이후 영상 대상
			youtubeSyncService.syncVideoStatisticsByPublishedAfter(java.time.LocalDateTime.now().minusHours(3));
		} catch (Exception e) {
			log.error("### [Scheduler] 최근 3시간 영상 통계 갱신 중 오류 발생 ###", e);
		}
	}

	/**
	 * 매 1시간마다 최근 24시간 이내에 업로드된 영상의 통계를 갱신합니다.
	 * Cron: 매 정각 0분 0초
	 */
	@Scheduled(cron = "0 0 * * * *")
	public void scheduleRecentDayVideosStatsSync() {
		log.info("### [Scheduler] 최근 24시간 영상 통계 갱신 시작 ###");
		try {
			// 현재 시간으로부터 24시간 전 이후 영상 대상
			youtubeSyncService.syncVideoStatisticsByPublishedAfter(java.time.LocalDateTime.now().minusDays(1));
		} catch (Exception e) {
			log.error("### [Scheduler] 최근 24시간 영상 통계 갱신 중 오류 발생 ###", e);
		}
	}

	/**
	 * 매일 새벽 3시(KST)에 최근 1주일간의 비디오 데이터(통계 포함)를 동기화합니다.
	 * Cron: 매일 03:00:00
	 */
	@Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
	public void scheduleLastWeekVideosSync() {
		log.info("### [Scheduler] 주간 비디오 데이터 및 통계 동기화 시작 ###");
		try {
			youtubeSyncService.syncLastWeekVideos();
		} catch (Exception e) {
			log.error("### [Scheduler] 주간 비디오 동기화 중 오류 발생 ###", e);
		}
	}
}
