package com.toy.nar.app.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.toy.nar.app.monitor.SchedulerAlertService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 탈퇴 기록({@code member_withdrawal}). 탈퇴 자체는 하드 딜리트 그대로이고, 지우기 직전에 한 줄 남긴다.
 * 스키마와 컬럼 의미는 V99 주석 참고.
 *
 * <p>ponytail: 엔티티·리포지토리 없이 JdbcTemplate 으로 쓴다. 앱이 이 테이블을 읽는 곳이 아직 없다
 * (재가입 제재 재적용이 생기면 그때 조회 경로를 붙인다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

	static final int HASH_RETENTION_DAYS = 30;
	// 영구 제재(until IS NULL)를 restricted_until 한 컬럼에 담기 위한 값.
	static final LocalDateTime PERMANENT = LocalDateTime.of(9999, 12, 31, 0, 0);

	private final JdbcTemplate jdbcTemplate;
	private final SchedulerAlertService schedulerAlertService;

	/**
	 * 탈퇴할 회원을 기록한다. 회원 삭제와 같은 트랜잭션에서, 삭제보다 먼저 불러야 한다.
	 * 회원이 이미 없거나(재호출) 이미 기록됐으면(연타) 아무것도 하지 않는다.
	 */
	public void record(Long memberId, LocalDateTime now) {
		List<String> socials = jdbcTemplate.queryForList(
				"SELECT CONCAT(provider, ':', provider_id) FROM member_social WHERE member_id = ? ORDER BY id",
				String.class, memberId);
		String providers = socials.stream().map(s -> s.substring(0, s.indexOf(':')))
				.collect(Collectors.joining(","));
		String hashes = socials.stream().map(MemberWithdrawalService::sha256Hex)
				.collect(Collectors.joining(","));

		// 회원 행에서 바로 SELECT 해 넣는다 — 회원이 없으면 0행이라 멱등하고,
		// 동시 탈퇴가 같은 회원을 두 번 넣으려 하면 UNIQUE(member_id) 를 IGNORE 가 흡수한다.
		jdbcTemplate.update("""
				INSERT IGNORE INTO member_withdrawal
				    (member_id, providers, social_id_hashes, joined_at, onboarded_at, withdrawn_at, restricted_until)
				SELECT m.id, ?, ?, m.created_at, m.onboarded_at, ?,
				       (SELECT CASE WHEN COUNT(*) = 0 THEN NULL
				                    WHEN SUM(r.until IS NULL) > 0 THEN ?
				                    ELSE MAX(r.until) END
				        FROM member_community_restriction r
				        WHERE r.member_id = m.id AND (r.until IS NULL OR r.until > ?))
				FROM member m
				WHERE m.id = ?
				""",
				providers, hashes.isEmpty() ? null : hashes, now, PERMANENT, now, memberId);
	}

	// 알림 보존 정리(04:40) 뒤.
	@Scheduled(cron = "${member.withdrawal.hash-clear-cron:0 50 4 * * *}", zone = "Asia/Seoul")
	public void clearExpiredHashesJob() {
		long startTime = System.currentTimeMillis();
		try {
			int cleared = clearExpiredHashes(LocalDateTime.now());
			schedulerAlertService.recordSuccess(
					"MEMBER_WITHDRAWAL_HASH_CLEAR",
					"탈퇴 기록 소셜 해시 비우기 (" + cleared + "행)",
					System.currentTimeMillis() - startTime);
		} catch (Exception e) {
			log.error("Member withdrawal hash clear failed", e);
			schedulerAlertService.recordFailure(
					"MEMBER_WITHDRAWAL_HASH_CLEAR", "탈퇴 기록 소셜 해시 비우기", e, "member_withdrawal 해시 NULL 처리 중 오류");
		}
	}

	/** 보관 기간이 지났고 제재도 끝난 행의 소셜 해시를 비운다. 비운 행 수를 반환한다. */
	int clearExpiredHashes(LocalDateTime now) {
		return jdbcTemplate.update("""
				UPDATE member_withdrawal SET social_id_hashes = NULL
				WHERE social_id_hashes IS NOT NULL
				  AND withdrawn_at < ?
				  AND (restricted_until IS NULL OR restricted_until <= ?)
				""", now.minusDays(HASH_RETENTION_DAYS), now);
	}

	/**
	 * ponytail: 키 없는 SHA-256 이다. 카카오처럼 숫자 id 는 대입으로 되돌릴 수 있어 완전한 가명처리는 아니다 —
	 * 30일 뒤 비우는 것으로 노출 기간을 묶는다. 더 막아야 하면 HMAC(전용 시크릿)으로 바꾼다.
	 */
	static String sha256Hex(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
