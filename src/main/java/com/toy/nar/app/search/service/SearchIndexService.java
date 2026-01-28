package com.toy.nar.app.search.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.common.util.HangulUtil;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import com.toy.nar.domain.search.document.SearchDocument;
import com.toy.nar.domain.search.repository.SearchDocumentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Elasticsearch 검색 인덱스 관리 서비스
 * - MySQL 데이터를 ES에 동기화
 * - Team, Player 검색 기능 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    private final SearchDocumentRepository searchDocumentRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    // LCK 팀 한글 이름 매핑 (하드코딩 - 키는 소문자로 저장)
    private static final Map<String, String> KOREAN_TEAM_NAMES = Map.of(
            "t1", "티원",
            "gen.g", "젠지",
            "hanwha life esports", "한화생명",
            "dplus kia", "디플러스 기아 담원",
            "kt rolster", "케이티 롤스터",
            "dn soopers", "디엔 수퍼스",
            "bnk fearx", "비엔케이 피어엑스",
            "nongshim redforce", "농심 레드포스",
            "hanjin brion", "한진 브리온",
            "drx", "디알엑스");

    /**
     * 키워드로 통합 검색 (Team + Player)
     */
    public List<SearchDocument> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return searchDocumentRepository.searchByKeyword(keyword.trim().toLowerCase());
    }

    /**
     * 팀만 검색
     */
    public List<SearchDocument> searchTeams(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return searchDocumentRepository.searchByTypeAndKeyword("TEAM", keyword.trim().toLowerCase());
    }

    /**
     * 선수만 검색
     */
    public List<SearchDocument> searchPlayers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return searchDocumentRepository.searchByTypeAndKeyword("PLAYER", keyword.trim().toLowerCase());
    }

    /**
     * 모든 팀 데이터를 ES에 동기화
     */
    @Transactional(readOnly = true)
    public int syncAllTeams() {
        List<Team> teams = teamRepository.findAll();
        int count = 0;

        for (Team team : teams) {
            String name = team.getName();
            String nameKorean = KOREAN_TEAM_NAMES.getOrDefault(name.toLowerCase(), null);

            // 초성 생성 (한글 이름이 있으면 한글 이름으로, 없으면 영문이라도 시도)
            String chosung = "";
            if (nameKorean != null) {
                chosung = HangulUtil.extractChosung(nameKorean);
            }

            // SearchDocument 생성 시 초성도 autocomplete에 포함
            SearchDocument doc = SearchDocument.builder()
                    .id("TEAM_" + team.getId())
                    .entityType("TEAM")
                    .entityId(team.getId())
                    .name(name)
                    .nameKorean(nameKorean)
                    .nameNormalized(name.toLowerCase().replaceAll("[^a-z0-9가-힣]", ""))
                    .autocomplete(name + " " + (nameKorean != null ? nameKorean : "") + " " + chosung)
                    .build();

            searchDocumentRepository.save(doc);
            count++;
        }

        log.info("### [SearchIndex] 팀 동기화 완료: {}개 (한글 매핑 적용) ###", count);
        return count;
    }

    /**
     * 모든 선수 데이터를 ES에 동기화
     */
    @Transactional(readOnly = true)
    public int syncAllPlayers() {
        List<Player> players = playerRepository.findAll();
        int count = 0;

        for (Player player : players) {
            SearchDocument doc = SearchDocument.ofPlayer(
                    player.getId(),
                    player.getName());
            searchDocumentRepository.save(doc);
            count++;
        }

        log.info("### [SearchIndex] 선수 동기화 완료: {}개 ###", count);
        return count;
    }

    /**
     * 전체 동기화 (Team + Player)
     */
    public void syncAll() {
        log.info("### [SearchIndex] 전체 동기화 시작 ###");
        int teamCount = syncAllTeams();
        int playerCount = syncAllPlayers();
        log.info("### [SearchIndex] 전체 동기화 완료: Team {}개, Player {}개 ###", teamCount, playerCount);
    }

    /**
     * 단일 팀 인덱싱
     */
    public void indexTeam(Team team) {
        String name = team.getName();
        String nameKorean = KOREAN_TEAM_NAMES.getOrDefault(name, null);
        String chosung = (nameKorean != null) ? HangulUtil.extractChosung(nameKorean) : "";

        SearchDocument doc = SearchDocument.builder()
                .id("TEAM_" + team.getId())
                .entityType("TEAM")
                .entityId(team.getId())
                .name(name)
                .nameKorean(nameKorean)
                .nameNormalized(name.toLowerCase().replaceAll("[^a-z0-9가-힣]", ""))
                .autocomplete(name + " " + (nameKorean != null ? nameKorean : "") + " " + chosung)
                .build();

        searchDocumentRepository.save(doc);
    }

    /**
     * 단일 선수 인덱싱
     */
    public void indexPlayer(Player player) {
        SearchDocument doc = SearchDocument.ofPlayer(player.getId(), player.getName());
        searchDocumentRepository.save(doc);
    }

    /**
     * 인덱스 삭제
     */
    public void deleteIndex(String entityType, Long entityId) {
        String id = entityType + "_" + entityId;
        searchDocumentRepository.deleteById(id);
    }
}
