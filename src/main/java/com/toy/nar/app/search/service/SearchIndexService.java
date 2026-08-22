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
import org.springframework.beans.factory.ObjectProvider;

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

    /** 색인은 관리자 수동 트리거다. ES 가 없으면 여기서 그대로 실패하는 게 맞다. */
    private final ObjectProvider<SearchDocumentRepository> searchDocumentRepositoryProvider;

    private SearchDocumentRepository searchDocumentRepository() {
        return searchDocumentRepositoryProvider.getObject();
    }
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    // LCK 팀 한글 이름 매핑 (하드코딩 - 키는 소문자로 저장)
    private static final Map<String, TeamSearchMetadata> TEAM_SEARCH_METADATA = Map.ofEntries(
            Map.entry("t1", new TeamSearchMetadata("티원", "SKT T1")),
            Map.entry("gen.g", new TeamSearchMetadata("젠지", "GenG, GEN")),
            Map.entry("hanwha life esports", new TeamSearchMetadata("한화생명", "HLE")),
            Map.entry("dplus kia", new TeamSearchMetadata("디플러스 기아 담원", "DK, 담원")),
            Map.entry("kt rolster", new TeamSearchMetadata("케이티 롤스터", "KT")),
            Map.entry("dn soopers", new TeamSearchMetadata("디엔 수퍼스", "DNS, SOOP")),
            Map.entry("bnk fearx", new TeamSearchMetadata("비엔케이 피어엑스", "BFX, FEARX")),
            Map.entry("nongshim redforce", new TeamSearchMetadata("농심 레드포스", "NS")),
            Map.entry("hanjin brion", new TeamSearchMetadata("한진 브리온", "BRO, BRION")),
            Map.entry("kiwoom drx", new TeamSearchMetadata("키움 디알엑스", "DRX, KRX, 디알엑스")));

    /**
     * 키워드로 통합 검색 (Team + Player)
     */
    public List<SearchDocument> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return searchDocumentRepository().searchByKeyword(keyword.trim().toLowerCase());
    }

    /**
     * 팀만 검색
     */
    public List<SearchDocument> searchTeams(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return searchDocumentRepository().searchByTypeAndKeyword("TEAM", keyword.trim().toLowerCase());
    }

    /**
     * 선수만 검색
     */
    public List<SearchDocument> searchPlayers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return searchDocumentRepository().searchByTypeAndKeyword("PLAYER", keyword.trim().toLowerCase());
    }

    /**
     * 모든 팀 데이터를 ES에 동기화 (team_code가 있는 주요 리그 팀만)
     */
    public int syncAllTeams() {
        // 기존 TEAM 인덱스 전체 삭제 후 재생성 (team_code 없는 팀 제거)
        searchDocumentRepository().deleteByEntityType("TEAM");
        log.info("### [SearchIndex] 기존 TEAM 인덱스 삭제 완료 ###");

        List<Team> teams = teamRepository.findAll();
        int count = 0;

        for (Team team : teams) {
            // team_code가 없는 팀은 인덱싱 제외 (주요 리그 팀만 인덱싱)
            if (team.getCode() == null || team.getCode().isBlank()) {
                continue;
            }

            String name = team.getName();
            TeamSearchMetadata metadata = TEAM_SEARCH_METADATA.get(name.toLowerCase());
            String nameKorean = metadata != null ? metadata.koreanName() : null;
            String aliases = metadata != null ? metadata.aliases() : null;

            // 초성 생성 (한글 이름이 있으면 한글 이름으로, 없으면 영문이라도 시도)
            String chosung = "";
            if (nameKorean != null) {
                chosung = HangulUtil.extractChosung(nameKorean);
            }

            // SearchDocument 생성 시 teamCode도 autocomplete에 포함
            SearchDocument doc = SearchDocument.builder()
                    .id("TEAM_" + team.getId())
                    .entityType("TEAM")
                    .entityId(team.getId())
                    .name(name)
                    .nameKorean(nameKorean)
                    .nameNormalized(name.toLowerCase().replaceAll("[^a-z0-9가-힣]", ""))
                    .autocomplete(
                            buildAutocomplete(name, nameKorean, chosung, team.getCode(), aliases))
                    .aliases(aliases)
                    .teamCode(team.getCode())
                    .teamImageUrl(team.getImageUrl())
                    .build();

            searchDocumentRepository().save(doc);
            count++;
        }

        log.info("### [SearchIndex] 팀 동기화 완료: {}개 (주요 리그 팀만, 한글 매핑 적용) ###", count);
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
            searchDocumentRepository().save(doc);
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
     * 단일 팀 인덱싱 (team_code 없으면 제외)
     */
    public void indexTeam(Team team) {
        // team_code가 없는 팀은 인덱싱 제외
        if (team.getCode() == null || team.getCode().isBlank()) {
            log.debug("Skipping indexing for team without code: {}", team.getName());
            return;
        }

        String name = team.getName();
        TeamSearchMetadata metadata = TEAM_SEARCH_METADATA.get(name.toLowerCase());
        String nameKorean = metadata != null ? metadata.koreanName() : null;
        String aliases = metadata != null ? metadata.aliases() : null;
        String chosung = (nameKorean != null) ? HangulUtil.extractChosung(nameKorean) : "";

        SearchDocument doc = SearchDocument.builder()
                .id("TEAM_" + team.getId())
                .entityType("TEAM")
                .entityId(team.getId())
                .name(name)
                .nameKorean(nameKorean)
                .nameNormalized(name.toLowerCase().replaceAll("[^a-z0-9가-힣]", ""))
                .autocomplete(
                        buildAutocomplete(name, nameKorean, chosung, team.getCode(), aliases))
                .aliases(aliases)
                .teamCode(team.getCode())
                .teamImageUrl(team.getImageUrl())
                .build();

        searchDocumentRepository().save(doc);
    }

    /**
     * 단일 선수 인덱싱
     */
    public void indexPlayer(Player player) {
        SearchDocument doc = SearchDocument.ofPlayer(player.getId(), player.getName());
        searchDocumentRepository().save(doc);
    }

    /**
     * 인덱스 삭제
     */
    public void deleteIndex(String entityType, Long entityId) {
        String id = entityType + "_" + entityId;
        searchDocumentRepository().deleteById(id);
    }

    private String buildAutocomplete(String name, String nameKorean, String chosung, String teamCode, String aliases) {
        return String.join(" ",
                name != null ? name : "",
                nameKorean != null ? nameKorean : "",
                chosung != null ? chosung : "",
                teamCode != null ? teamCode : "",
                aliases != null ? aliases : "").trim();
    }
}
