package com.toy.nar.api.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원 목록은 native 쿼리라 정렬 프로퍼티가 SQL 로 그대로 나간다.
 * 화이트리스트가 무너지면 임의 문자열이 ORDER BY 에 실려 500(또는 그 이상)이 된다.
 */
class BackofficeMemberSortTest {

	@Test
	@DisplayName("허용된 정렬 컬럼은 그대로 유지한다")
	void keepsAllowedSort() {
		var sorted = BackofficeController.sanitizeMemberSort(
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "favoritePlayerCount")));

		assertThat(sorted.getSort().getOrderFor("favoritePlayerCount")).isNotNull();
	}

	@Test
	@DisplayName("목록에 없는 정렬 프로퍼티는 버린다")
	void dropsUnknownSort() {
		var sorted = BackofficeController.sanitizeMemberSort(
				PageRequest.of(1, 20, Sort.by("createdAt").descending().and(Sort.by("drop table"))));

		assertThat(sorted.getSort().stream().map(Sort.Order::getProperty)).containsExactly("createdAt");
		assertThat(sorted.getPageNumber()).isEqualTo(1);
		assertThat(sorted.getPageSize()).isEqualTo(20);
	}
}
