package com.toy.nar.game;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.game.dto.CategoryTree;
import com.toy.nar.game.dto.TeamSummary;
import com.toy.nar.game.service.CategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

	private final CategoryService categoryService;

	@GetMapping("/tree")
	public ResponseEntity<CategoryTree> getCategoryTree() {
		CategoryTree tree = categoryService.buildCategoryTree();
		return ResponseEntity.ok(tree);
	}

	@GetMapping("/teams")
	public ResponseEntity<List<TeamSummary>> getTeams(
		@RequestParam String leagueName,
		@RequestParam String split) {
		List<TeamSummary> teams = categoryService.getTeamSummaries(leagueName, split);
		return ResponseEntity.ok(teams);
	}
}
