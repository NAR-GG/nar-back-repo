package com.toy.nar.domain.member.entity;

import com.toy.nar.domain.participant.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 즐겨찾기 선수 교체가 기존 항목을 유지하는지 지키는 회귀 테스트.
 *
 * 예전엔 전부 clear 한 뒤 다시 add 해서, 같은 선수를 유지하는 재온보딩에서 Hibernate 가
 * 컬렉션 삭제보다 INSERT 를 먼저 flush 해 유니크 제약을 위반했다
 * (실측 2026-07-29 23:27:55 POST /api/auth/onboarding → 500).
 * 같은 선수가 남아 있으면 새 INSERT 가 생기지 않아 위반 자체가 발생하지 않는다.
 */
class MemberFavoritePlayerReplaceTest {

	@Test
	@DisplayName("같은 선수로 재온보딩하면 기존 항목을 그대로 유지한다")
	void 재온보딩해도_기존_항목을_유지한다() {
		Player faker = player(1L, "Faker");
		Player oner = player(2L, "Oner");
		Member member = member();

		member.completeOnboarding("LCK", null, List.of(faker, oner));
		List<MemberFavoritePlayer> first = List.copyOf(member.getFavoritePlayers());

		member.completeOnboarding("LCK", null, List.of(faker, oner));

		// 같은 인스턴스가 유지돼야 한다 — 새로 만들면 INSERT 가 나가 유니크 제약을 위반한다.
		assertThat(member.getFavoritePlayers()).containsExactlyElementsOf(first);
	}

	@Test
	@DisplayName("빠진 선수는 지우고 새 선수만 추가한다")
	void 차집합만_반영한다() {
		Player faker = player(1L, "Faker");
		Player oner = player(2L, "Oner");
		Player keria = player(3L, "Keria");
		Member member = member();
		member.completeOnboarding("LCK", null, List.of(faker, oner));
		MemberFavoritePlayer keptFaker = member.getFavoritePlayers().stream()
				.filter(f -> f.getPlayer().getId().equals(1L))
				.findFirst()
				.orElseThrow();

		member.completeOnboarding("LCK", null, List.of(faker, keria));

		assertThat(member.getFavoritePlayers())
				.extracting(f -> f.getPlayer().getId())
				.containsExactlyInAnyOrder(1L, 3L);
		assertThat(member.getFavoritePlayers()).contains(keptFaker);
	}

	@Test
	@DisplayName("선수 목록이 비면 전부 지운다")
	void 빈_목록이면_모두_지운다() {
		Member member = member();
		member.completeOnboarding("LCK", null, List.of(player(1L, "Faker")));

		member.completeOnboarding("LCK", null, List.of());

		assertThat(member.getFavoritePlayers()).isEmpty();
	}

	@Test
	@DisplayName("중복 입력은 한 건으로 접는다")
	void 중복_입력을_접는다() {
		Player faker = player(1L, "Faker");
		Member member = member();

		member.completeOnboarding("LCK", null, List.of(faker, faker));

		assertThat(member.getFavoritePlayers()).hasSize(1);
	}

	private Member member() {
		Member member = Member.builder().name("나르").tag("KR1").build();
		ReflectionTestUtils.setField(member, "id", 100L);
		return member;
	}

	private Player player(Long id, String name) {
		Player player = Player.builder().name(name).build();
		ReflectionTestUtils.setField(player, "id", id);
		return player;
	}
}
