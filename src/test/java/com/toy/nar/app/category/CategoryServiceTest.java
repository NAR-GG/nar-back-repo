package com.toy.nar.app.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.toy.nar.app.category.dto.CategoryPatchQueryDto;
import com.toy.nar.app.category.dto.CategoryQueryDto;
import com.toy.nar.app.category.dto.CategoryTree;
import com.toy.nar.app.category.dto.SplitCategory;
import com.toy.nar.domain.game.repository.LeagueRepository;
import com.toy.nar.domain.game.repository.LeagueTeamRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

	@Mock
	private LeagueRepository leagueRepository;

	@Mock
	private LeagueTeamRepository leagueTeamRepository;

	private CategoryService categoryService;

	@BeforeEach
	void setUp() {
		categoryService = new CategoryService(leagueRepository, leagueTeamRepository);
	}

	@Test
	@DisplayName("카테고리 트리는 스플릿별 패치 목록을 중복 없이 내림차순으로 포함한다")
	void buildCategoryTree_includesSplitPatches() {
		when(leagueRepository.findAllCategoryDataByYear(2026)).thenReturn(List.of(
				new CategoryQueryDto("LCK", "Round 1-2", 10L, 1L, "T1"),
				new CategoryQueryDto("LCK", "Round 1-2", 10L, 2L, "GEN"),
				new CategoryQueryDto("LCK", "Round 3-5", 11L, 1L, "T1")));

		when(leagueRepository.findDistinctPatchesByYear(2026)).thenReturn(List.of(
				new CategoryPatchQueryDto("LCK", "Round 1-2", "14.9"),
				new CategoryPatchQueryDto("LCK", "Round 1-2", "14.10"),
				new CategoryPatchQueryDto("LCK", "Round 1-2", "14.11"),
				new CategoryPatchQueryDto("LCK", "Round 1-2", "14.9"),
				new CategoryPatchQueryDto("LCK", "Round 3-5", "15.1"),
				new CategoryPatchQueryDto("LCK", "Round 3-5", "14.20")));

		CategoryTree result = categoryService.buildCategoryTree(2026);

		assertThat(result.seasons()).hasSize(1);
		SplitCategory round12 = findSplit(result, "Round 1-2");
		SplitCategory round35 = findSplit(result, "Round 3-5");

		assertThat(round12.patches()).containsExactly("14.11", "14.10", "14.9");
		assertThat(round35.patches()).containsExactly("15.1", "14.20");

		verify(leagueRepository).findAllCategoryDataByYear(2026);
		verify(leagueRepository).findDistinctPatchesByYear(2026);
	}

	private SplitCategory findSplit(CategoryTree tree, String splitName) {
		return tree.seasons().stream()
				.flatMap(season -> season.leagues().stream())
				.flatMap(league -> league.splits().stream())
				.filter(split -> split.name().equals(splitName))
				.findFirst()
				.orElseThrow();
	}
}
