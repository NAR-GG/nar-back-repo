package com.toy.nar.domain.rating.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.rating.entity.LivePlayerRating;

/** 홈 평점 탭 쿼리 — 한줄평 필터·차단 제외(NOT IN)·id 커서가 실제로 도는지 본다. */
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
class LivePlayerRatingRecentQueryTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private LivePlayerRatingRepository repository;

	private Member alice;
	private Member blocked;
	private final List<Long> ids = new java.util.ArrayList<>();

	@BeforeEach
	void setUp() {
		alice = em.persist(Member.builder().name("앨리스").tag("0001").email("a@test").build());
		blocked = em.persist(Member.builder().name("차단됨").tag("0002").email("b@test").build());
		rate(alice, 1, "라인전부터 다름");
		rate(alice, 2, "   "); // 공백 한줄평은 저장 시 NULL
		rate(blocked, 3, "차단된 사람 글");
		rate(alice, 4, "한타 좋았음");
		rate(alice, 5, null);
		em.flush();
	}

	private void rate(Member member, int participantId, String comment) {
		LivePlayerRating rating = em.persist(new LivePlayerRating(
				"m1", "g1", participantId, member, null, "BLUE", "mid", "Faker", null, "아리", 4, comment));
		ids.add(rating.getId());
	}

	@Test
	void 한줄평이_있고_차단되지_않은_것만_최신순() {
		List<LivePlayerRating> rows = repository.findRecentWithComment(
				null, List.of(blocked.getId()), PageRequest.of(0, 10));

		assertThat(rows).extracting(LivePlayerRating::getComment).containsExactly("한타 좋았음", "라인전부터 다름");
	}

	@Test
	void 커서_뒤만_읽고_센티널로_빈_차단_목록을_대신한다() {
		Long cursor = ids.get(3); // "한타 좋았음" 의 id

		List<LivePlayerRating> rows = repository.findRecentWithComment(
				cursor, List.of(-1L), PageRequest.of(0, 10));

		assertThat(rows).extracting(LivePlayerRating::getComment).containsExactly("차단된 사람 글", "라인전부터 다름");
	}
}
