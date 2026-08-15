package com.toy.nar.domain.rating.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.rating.entity.LivePlayerRating;

/**
 * 백오피스 리뷰 검색 쿼리({@link LivePlayerRatingRepository#searchForBackoffice})를 검증한다.
 *
 * <p>마이그레이션은 MySQL 전용 문법이라 H2에서는 엔티티 기반 스키마(create-drop)를 사용한다.
 */
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class LivePlayerRatingSearchTest {

	@Autowired
	private LivePlayerRatingRepository ratingRepository;

	@PersistenceContext
	private EntityManager em;

	@BeforeEach
	void setUp() {
		Member faker = new Member("페이커팬", "1234", "fan1@nar.kr");
		Member other = new Member("구마유시팬", "5678", "fan2@nar.kr");
		em.persist(faker);
		em.persist(other);

		ratingRepository.save(rating("m1", "Faker", faker, 5, "캐리했다"));
		ratingRepository.save(rating("m1", "Gumayusi", other, 2, "던짐"));
		ratingRepository.save(rating("m2", "Faker", other, 5, null));
	}

	@Test
	@DisplayName("q가 null이면 전체, 값이 있으면 선수명·회원명·한줄평 부분일치로 검색한다")
	void searchForBackoffice_matchesPlayerOrMemberOrComment() {
		assertThat(ratingRepository.searchForBackoffice(null, "all", null, PageRequest.of(0, 10)).getTotalElements())
				.isEqualTo(3);

		// 선수명 부분일치
		assertThat(names(ratingRepository.searchForBackoffice("Faker", "all", null, PageRequest.of(0, 10)).getContent()))
				.containsExactly("Faker", "Faker");

		// 회원명 부분일치 — 그 회원이 쓴 리뷰 전부
		assertThat(names(ratingRepository.searchForBackoffice("구마유시팬", "all", null, PageRequest.of(0, 10)).getContent()))
				.containsExactlyInAnyOrder("Gumayusi", "Faker");

		// 한줄평 부분일치
		assertThat(names(ratingRepository.searchForBackoffice("던짐", "all", null, PageRequest.of(0, 10)).getContent()))
				.containsExactly("Gumayusi");

		// 매칭 없음
		assertThat(ratingRepository.searchForBackoffice("zzz", "all", null, PageRequest.of(0, 10)).getTotalElements())
				.isZero();
	}

	@Test
	@DisplayName("rating을 주면 해당 별점만, q와 함께도 동작한다")
	void searchForBackoffice_filtersByRating() {
		assertThat(ratingRepository.searchForBackoffice(null, "all", 5, PageRequest.of(0, 10)).getTotalElements())
				.isEqualTo(2);
		assertThat(names(ratingRepository.searchForBackoffice("던짐", "all", 2, PageRequest.of(0, 10)).getContent()))
				.containsExactly("Gumayusi");
		// q와 rating이 서로 다른 행을 가리키면 결과 없음
		assertThat(ratingRepository.searchForBackoffice("던짐", "all", 5, PageRequest.of(0, 10)).getTotalElements())
				.isZero();
	}

	@Test
	@DisplayName("field 로 검색 대상을 좁힌다 (player|member|comment)")
	void searchForBackoffice_scopesByField() {
		// "Faker" 는 선수명에만 있다 → member/comment 스코프면 0건
		assertThat(ratingRepository.searchForBackoffice("Faker", "player", null, PageRequest.of(0, 10))
				.getTotalElements()).isEqualTo(2);
		assertThat(ratingRepository.searchForBackoffice("Faker", "member", null, PageRequest.of(0, 10))
				.getTotalElements()).isZero();
		assertThat(ratingRepository.searchForBackoffice("Faker", "comment", null, PageRequest.of(0, 10))
				.getTotalElements()).isZero();

		// "팬" 은 작성자 닉네임에만 있다
		assertThat(ratingRepository.searchForBackoffice("팬", "member", null, PageRequest.of(0, 10))
				.getTotalElements()).isEqualTo(3);
		assertThat(ratingRepository.searchForBackoffice("팬", "player", null, PageRequest.of(0, 10))
				.getTotalElements()).isZero();

		// 한줄평 스코프 + 닉네임 태그(#) 검색
		assertThat(names(ratingRepository.searchForBackoffice("캐리", "comment", null, PageRequest.of(0, 10))
				.getContent())).containsExactly("Faker");
		assertThat(ratingRepository.searchForBackoffice("#5678", "member", null, PageRequest.of(0, 10))
				.getTotalElements()).isEqualTo(2);
	}

	private static List<String> names(List<LivePlayerRating> rows) {
		return rows.stream().map(LivePlayerRating::getPlayerName).toList();
	}

	private static LivePlayerRating rating(String matchId, String playerName, Member member, int rating, String comment) {
		return new LivePlayerRating(matchId, matchId + "-1", 1, member, null,
				"blue", "MID", playerName, playerName, "Ahri", rating, comment);
	}

	/**
	 * 컨텍스트를 rating·member·participant 도메인으로 한정한다. (전체 앱을 띄우면 Elasticsearch 빈을
	 * 요구해 슬라이스 테스트가 실패하므로, 저장소의 다른 리포지토리 테스트와 동일하게 범위를 좁힌다.)
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EntityScan(basePackages = {
			"com.toy.nar.domain.rating.entity",
			"com.toy.nar.domain.member.entity",
			"com.toy.nar.domain.participant.entity",
			"com.toy.nar.domain.game.entity"
	})
	@EnableJpaRepositories(basePackageClasses = LivePlayerRatingRepository.class)
	static class TestJpaConfiguration {
	}
}
