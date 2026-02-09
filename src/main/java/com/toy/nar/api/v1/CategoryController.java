package com.toy.nar.api.v1;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.category.CategoryService;
import com.toy.nar.app.category.dto.CategoryTree;
import com.toy.nar.app.category.dto.TeamSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "1.1 카테고리 정보", description = "리그, 스플릿, 팀 정보를 트리 형식으로 조회합니다.")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

	private final CategoryService categoryService;

	@Operation(summary = "카테고리 계층 구조 조회", description = "리그 > 시즌 > 팀 순서로 구성된 전체 트리 데이터를 반환합니다.")
	@GetMapping("/tree")
	public ResponseEntity<CategoryTree> getCategoryTree(
			@RequestParam(value = "year", defaultValue = "2026") int year) {
		CategoryTree tree = categoryService.buildCategoryTree(year);
		return ResponseEntity.ok(tree);
	}

}
