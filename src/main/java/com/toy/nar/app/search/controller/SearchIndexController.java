package com.toy.nar.app.search.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.search.service.SearchIndexService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin - Search Index", description = "검색 인덱스 관리 API")
@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
public class SearchIndexController {

    private final SearchIndexService searchIndexService;

    @Operation(summary = "전체 동기화", description = "MySQL의 Team, Player 데이터를 Elasticsearch에 동기화")
    @PostMapping("/sync")
    public ResponseEntity<String> syncAll() {
        searchIndexService.syncAll();
        return ResponseEntity.ok("동기화 완료");
    }

    @Operation(summary = "팀 동기화", description = "Team 데이터만 동기화")
    @PostMapping("/sync/teams")
    public ResponseEntity<String> syncTeams() {
        int count = searchIndexService.syncAllTeams();
        return ResponseEntity.ok("팀 동기화 완료: " + count + "개");
    }

    @Operation(summary = "선수 동기화", description = "Player 데이터만 동기화")
    @PostMapping("/sync/players")
    public ResponseEntity<String> syncPlayers() {
        int count = searchIndexService.syncAllPlayers();
        return ResponseEntity.ok("선수 동기화 완료: " + count + "개");
    }
}
