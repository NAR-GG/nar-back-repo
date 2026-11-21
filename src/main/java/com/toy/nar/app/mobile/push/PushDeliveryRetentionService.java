package com.toy.nar.app.mobile.push;

import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.domain.member.entity.PushDeliveryStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 푸시 발송 기록({@code player_solo_rank_push_delivery}, {@code member_team_event_push_delivery}) 보존 정책.
 *
 * <p>이 두 테이블은 중복 발송 방지(유니크 키 + {@code INSERT IGNORE})와 지연·장애 조사에만 쓰인다.
 * 둘 다 이벤트가 끝난 뒤 며칠이면 다시 읽히지 않는데 보존 정책이 없어 2026-07 부터 전량이 쌓였다
 * (2026-11-21 실측: 솔랭 800만 행·1.9GB, 하루 약 8.5만 행).
 *
 * <p>기본 35일인 이유: 원본은 DB 덤프의 월별 사본({@code monthly/}, 매월 1일, 365일 보관)에 남긴다.
 * 30일이면 31일짜리 달의 1일에 생긴 행이 다음 1일 덤프 전에 지워져 어떤 월별 사본에도 안 들어간다.
 * 35일이면 모든 행이 최소 한 번 월별 사본에 실린다 — 따로 아카이브를 두지 않는 근거다.
 *
 * <p>삭제는 {@code (status, created_at)} 인덱스를 타도록 상태별로 나눠 청크로 지운다.
 * 각 청크는 자동 커밋이라 긴 트랜잭션·락을 만들지 않는다.
 */
@Slf4j
@Service
public class PushDeliveryRetentionService {

	private static final List<String> TABLES = List.of(
			"player_solo_rank_push_delivery",
			"member_team_event_push_delivery");

	private final JdbcTemplate jdbcTemplate;
	private final SchedulerAlertService schedulerAlertService;
	private final int retentionDays;
	private final int chunkSize;
	private final int maxChunksPerStatus;

	public PushDeliveryRetentionService(
			JdbcTemplate jdbcTemplate,
			SchedulerAlertService schedulerAlertService,
			@Value("${push.delivery.retention.days:35}") int retentionDays,
			@Value("${push.delivery.retention.chunk-size:5000}") int chunkSize,
			@Value("${push.delivery.retention.max-chunks-per-status:400}") int maxChunksPerStatus) {
		this.jdbcTemplate = jdbcTemplate;
		this.schedulerAlertService = schedulerAlertService;
		this.retentionDays = retentionDays;
		this.chunkSize = chunkSize;
		this.maxChunksPerStatus = maxChunksPerStatus;
	}

	// 알림 피드 보존(04:40) 뒤에 돈다. DB 백업(04:25)이 끝난 뒤라 그날 덤프에는 지우기 전 행이 실린다.
	@Scheduled(cron = "${push.delivery.retention.cron:0 50 4 * * *}", zone = "Asia/Seoul")
	public void purgeExpiredDeliveries() {
		long startTime = System.currentTimeMillis();
		try {
			int total = purge(LocalDateTime.now());
			schedulerAlertService.recordSuccess(
					"PUSH_DELIVERY_RETENTION",
					"푸시 발송 기록 보존 정리 (삭제 " + total + "행)",
					System.currentTimeMillis() - startTime);
		} catch (Exception e) {
			log.error("Push delivery retention purge failed", e);
			schedulerAlertService.recordFailure(
					"PUSH_DELIVERY_RETENTION",
					"푸시 발송 기록 보존 정리",
					e,
					"push_delivery 보존 기간 삭제 중 오류");
		}
	}

	/** 보존 기간이 지난 행을 테이블·상태별 청크로 지운다. 삭제한 총 행 수를 반환한다. */
	int purge(LocalDateTime now) {
		LocalDateTime cutoff = now.minusDays(retentionDays);
		int total = 0;
		for (String table : TABLES) {
			int deleted = 0;
			for (PushDeliveryStatus status : PushDeliveryStatus.values()) {
				deleted += purgeStatus(table, status, cutoff);
			}
			if (deleted > 0) {
				log.info("Purged {} {} rows older than {}", deleted, table, cutoff);
			}
			total += deleted;
		}
		return total;
	}

	private int purgeStatus(String table, PushDeliveryStatus status, LocalDateTime cutoff) {
		// 테이블명은 위 상수에서만 온다 — 외부 입력이 아니다.
		String sql = "DELETE FROM " + table + " WHERE status = ? AND created_at < ? LIMIT ?";
		int deleted = 0;
		for (int chunk = 0; chunk < maxChunksPerStatus; chunk++) {
			int rows = jdbcTemplate.update(sql, status.name(), cutoff, chunkSize);
			deleted += rows;
			if (rows < chunkSize) {
				return deleted;
			}
		}
		// 첫 실행은 수백만 행이라 상한에 걸린다. 새벽 내내 돌지 않고 다음 실행에 이어 지운다.
		log.warn("Push delivery purge hit chunk cap for {} {}. {} rows deleted, more remain.",
				table, status, deleted);
		return deleted;
	}
}
