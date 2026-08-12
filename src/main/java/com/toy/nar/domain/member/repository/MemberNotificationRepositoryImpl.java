package com.toy.nar.domain.member.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class MemberNotificationRepositoryImpl implements MemberNotificationRepositoryCustom {

	/** 한 INSERT 에 담을 최대 행 수. SQL 이 과하게 길어지지 않게 나눈다. */
	private static final int CHUNK_SIZE = 500;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final JdbcTemplate jdbcTemplate;

	@Override
	public int insertAll(
			Collection<Long> memberIds,
			MemberNotificationType type,
			String title,
			String body,
			Map<String, String> data) {
		if (memberIds == null || memberIds.isEmpty() || type == null || title == null) {
			return 0;
		}
		List<Long> targets = memberIds.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toCollection(LinkedHashSet::new))
				.stream()
				.toList();
		if (targets.isEmpty()) {
			return 0;
		}
		String dataJson = toJson(data);

		int inserted = 0;
		for (int start = 0; start < targets.size(); start += CHUNK_SIZE) {
			List<Long> chunk = targets.subList(start, Math.min(start + CHUNK_SIZE, targets.size()));
			// created_at 은 DB NOW() 가 아니라 애플리케이션 시각을 쓴다 — 기존 엔티티 경로
			// (LocalDateTime.now())와 같은 시간대여야 피드 정렬이 섞이지 않는다. DB 서버는 UTC 다.
			String values = chunk.stream()
					.map(id -> "(?, ?, ?, ?, ?, NULL, ?)")
					.collect(Collectors.joining(", "));
			List<Object> params = new ArrayList<>();
			java.time.LocalDateTime now = java.time.LocalDateTime.now();
			for (Long memberId : chunk) {
				params.add(memberId);
				params.add(type.name());
				params.add(title);
				params.add(body);
				params.add(dataJson);
				params.add(now);
			}
			inserted += jdbcTemplate.update(
					"INSERT INTO member_notification"
							+ " (member_id, type, title, body, data, read_at, created_at) VALUES "
							+ values,
					params.toArray());
		}
		return inserted;
	}

	private String toJson(Map<String, String> data) {
		if (data == null || data.isEmpty()) {
			return null;
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			// data 는 딥링크용 부가 정보다. 직렬화가 깨져도 알림 자체는 남기는 게 낫다.
			log.warn("알림 data 직렬화 실패 — data 없이 적재한다: {}", e.getMessage());
			return null;
		}
	}
}
