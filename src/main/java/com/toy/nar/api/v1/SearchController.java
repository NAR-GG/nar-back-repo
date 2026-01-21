package com.toy.nar.api.v1;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.search.dto.SearchAutocompleteResponse;
import com.toy.nar.app.search.service.SearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "7. 검색", description = "경기 및 팀 검색 자동완성 API")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "경기 자동완성 검색", description = "팀 vs 팀 형식의 검색어로 관련 경기를 자동완성 검색합니다. 예: 'T1 vs Geng', 'T1vsGeng', 't1 geng'")
    @GetMapping("/autocomplete")
    public ResponseEntity<SearchAutocompleteResponse> autocomplete(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {

        SearchAutocompleteResponse response = searchService.searchMatchAutocomplete(query, Math.min(limit, 10));
        return ResponseEntity.ok(response);
    }
}
