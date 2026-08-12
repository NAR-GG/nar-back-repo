package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberNotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 알림 피드 적재가 구독자 수와 무관하게 상수 왕복인지 지키는 테스트.
 *
 * <p>{@code saveAll} 은 이 엔티티에서 배치가 안 된다 — id 가 {@code GenerationType.IDENTITY} 라
 * Hibernate 가 INSERT 배치를 비활성화한다. 프로덕션(앱 EC2 서울 / DB OCI 춘천, 왕복 10ms 대)에서
 * 구독 1,440명 적재가 약 20초였고, 팬아웃이 발송 전에 피드를 남기므로 그게 곧 푸시 지연이 된다.</p>
 */
class MemberNotificationRepositoryImplTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
	private final MemberNotificationRepositoryImpl repository =
			new MemberNotificationRepositoryImpl(jdbcTemplate);

	@Test
	@DisplayName("구독자 1,440명을 500행씩 3번의 INSERT 로 적재한다")
	void 구독자_수와_무관하게_왕복이_상수다() {
		when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(500);

		repository.insertAll(
				LongStream.rangeClosed(1, 1_440).boxed().toList(),
				MemberNotificationType.PLAYER_SOLO_RANK_STARTED,
				"Zeus 선수가 솔랭을 시작했어요",
				"아리로 솔로 랭크 플레이 중",
				Map.of("type", "PLAYER_SOLO_RANK_STARTED", "playerId", "383"));

		// 1,440 = 500 + 500 + 440 → 3회. 행마다 왕복하면 1,440회다.
		verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
	}

	@Test
	@DisplayName("한 INSERT 에 행 수만큼 VALUES 를 싣고 컬럼 7개를 바인딩한다")
	void 다중_VALUES_로_넣는다() {
		when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(3);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);

		repository.insertAll(
				List.of(7L, 8L, 9L),
				MemberNotificationType.SET_START,
				"T1 vs GEN 1세트 시작",
				"1세트 시작",
				Map.of("matchId", "115548147900750245"));

		verify(jdbcTemplate).update(sql.capture(), params.capture());
		assertThat(sql.getValue()).startsWith("INSERT INTO member_notification");
		assertThat(sql.getValue()).contains("(?, ?, ?, ?, ?, NULL, ?), (?, ?, ?, ?, ?, NULL, ?), (?, ?, ?, ?, ?, NULL, ?)");
		// member_id, type, title, body, data, created_at → 행당 6개 바인딩(read_at 은 상수 NULL)
		assertThat(params.getValue()).hasSize(18);
		assertThat(params.getValue()[0]).isEqualTo(7L);
		assertThat(params.getValue()[1]).isEqualTo("SET_START");
		// data 는 JSON 문자열로 직렬화돼 들어간다.
		assertThat(params.getValue()[4]).isEqualTo("{\"matchId\":\"115548147900750245\"}");
	}

	@Test
	@DisplayName("같은 회원이 두 번 들어와도 한 행만 적재한다")
	void 중복_회원은_한_번만_넣는다() {
		when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
		ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);

		repository.insertAll(
				List.of(7L, 7L, 7L),
				MemberNotificationType.LIVE_EVENT,
				"제목",
				"본문",
				null);

		verify(jdbcTemplate).update(anyString(), params.capture());
		assertThat(params.getValue()).hasSize(6);
		// data 가 없으면 컬럼도 null 이다.
		assertThat(params.getValue()[4]).isNull();
	}

	@Test
	@DisplayName("대상이 없거나 필수 값이 비면 DB 를 건드리지 않는다")
	void 빈_입력은_왕복하지_않는다() {
		assertThat(repository.insertAll(List.of(), MemberNotificationType.SET_END, "제목", null, null)).isZero();
		assertThat(repository.insertAll(List.of(7L), null, "제목", null, null)).isZero();
		assertThat(repository.insertAll(List.of(7L), MemberNotificationType.SET_END, null, null, null)).isZero();
		verifyNoInteractions(jdbcTemplate);
	}
}
