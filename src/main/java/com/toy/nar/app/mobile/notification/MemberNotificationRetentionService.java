package com.toy.nar.app.mobile.notification;

import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import com.toy.nar.domain.member.repository.MemberNotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * 알림 피드({@code member_notification}) 보존 정책.
 *
 * <p>보존 기간을 타입별로 나눈 이유는 생성량과 열람률이 극단적으로 다르기 때문이다
 * (2026-08-04 프로덕션 실측): LIVE_EVENT 는 하루 약 2.7만 건이 쌓이는데 열람률이 0.13%,
 * PLAYER_SOLO_RANK_STARTED 는 하루 약 2.6만 건에 2.9%다. 반면 SET_START/SET_END 는
 * 둘을 합쳐 하루 5.8천 건뿐이라 오래 둬도 부담이 없고, 응원 팀 경기 기록이라 회고 가치가 있다.
 *
 * <p>보존 정책은 테이블을 줄이는 게 아니라 <b>무한 증가를 멈추는</b> 장치다. 유입이 하루 6만 건이면
 * 어떤 창을 잡아도 그 창만큼은 유지된다. 위 값 기준 정상 상태는 약 55만 행이다.
 *
 * <p>주의: 무한스크롤 피드가 여기서 잘린다. 알림 목록 API 는 날짜 필터가 없어 보존 기간이 곧
 * 사용자가 거슬러 올라갈 수 있는 한계가 된다.
 */
@Slf4j
@Service
public class MemberNotificationRetentionService {

	private final MemberNotificationRepository notificationRepository;
	private final SchedulerAlertService schedulerAlertService;
	private final Map<MemberNotificationType, Integer> retentionDays;
	private final int chunkSize;
	private final int maxChunksPerType;

	public MemberNotificationRetentionService(
			MemberNotificationRepository notificationRepository,
			SchedulerAlertService schedulerAlertService,
			@Value("${notification.retention.live-event-days:7}") int liveEventDays,
			@Value("${notification.retention.solo-rank-days:7}") int soloRankDays,
			@Value("${notification.retention.set-days:30}") int setDays,
			@Value("${notification.retention.chunk-size:5000}") int chunkSize,
			@Value("${notification.retention.max-chunks-per-type:400}") int maxChunksPerType) {
		this.notificationRepository = notificationRepository;
		this.schedulerAlertService = schedulerAlertService;
		this.chunkSize = chunkSize;
		this.maxChunksPerType = maxChunksPerType;

		this.retentionDays = new EnumMap<>(MemberNotificationType.class);
		retentionDays.put(MemberNotificationType.LIVE_EVENT, liveEventDays);
		retentionDays.put(MemberNotificationType.PLAYER_SOLO_RANK_STARTED, soloRankDays);
		retentionDays.put(MemberNotificationType.SET_START, setDays);
		retentionDays.put(MemberNotificationType.SET_END, setDays);
	}

	// 트래픽이 가장 적은 새벽에 돈다. DB 백업(04:10)과 겹치지 않게 04:40.
	@Scheduled(cron = "${notification.retention.cron:0 40 4 * * *}", zone = "Asia/Seoul")
	public void purgeExpiredNotifications() {
		long startTime = System.currentTimeMillis();
		try {
			int total = purge(LocalDateTime.now());
			schedulerAlertService.recordSuccess(
					"MEMBER_NOTIFICATION_RETENTION",
					"알림 피드 보존 정리 (삭제 " + total + "행)",
					System.currentTimeMillis() - startTime);
		} catch (Exception e) {
			log.error("Member notification retention purge failed", e);
			schedulerAlertService.recordFailure(
					"MEMBER_NOTIFICATION_RETENTION",
					"알림 피드 보존 정리",
					e,
					"member_notification 보존 기간 삭제 중 오류");
		}
	}

	/** 타입별 보존 기간이 지난 행을 청크 단위로 지운다. 삭제한 총 행 수를 반환한다. */
	int purge(LocalDateTime now) {
		int total = 0;
		for (MemberNotificationType type : MemberNotificationType.values()) {
			Integer days = retentionDays.get(type);
			if (days == null) {
				// 새 타입이 추가됐는데 보존 설정을 안 넣으면 조용히 영구 누적된다.
				log.warn("No retention configured for notification type {}. Rows will accumulate.", type);
				continue;
			}
			total += purgeType(type, now.minusDays(days));
		}
		return total;
	}

	private int purgeType(MemberNotificationType type, LocalDateTime cutoff) {
		int deleted = 0;
		for (int chunk = 0; chunk < maxChunksPerType; chunk++) {
			int rows = notificationRepository.deleteOlderThanByType(type.name(), cutoff, chunkSize);
			deleted += rows;
			if (rows < chunkSize) {
				break;
			}
			if (chunk == maxChunksPerType - 1) {
				// 한 번에 다 지우려다 새벽 내내 도는 것보다, 남기고 다음 실행에 이어 지우는 편이 안전하다.
				log.warn("Retention purge hit chunk cap for {}. {} rows deleted, more remain.", type, deleted);
			}
		}
		if (deleted > 0) {
			log.info("Purged {} {} notifications older than {}", deleted, type, cutoff);
		}
		return deleted;
	}
}
