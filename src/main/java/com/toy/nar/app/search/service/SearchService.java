package com.toy.nar.app.search.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.search.dto.MatchSuggestionDto;
import com.toy.nar.app.search.dto.SearchAutocompleteResponse;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.GameRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;
import com.toy.nar.domain.search.document.SearchDocument;
import com.toy.nar.domain.search.repository.SearchDocumentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final TeamRepository teamRepository;
    private final GameRepository gameRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final SearchDocumentRepository searchDocumentRepository;

    /**
     * 팀 vs 팀 경기 자동완성 검색
     * 초성 검색, 한글 검색, 영문 검색 모두 지원 (Elasticsearch + Nori)
     * 
     * @param query 검색어 (예: "T1vsGeng", "ㅈㅈ", "젠지")
     * @param limit 최대 결과 수
     */
    public SearchAutocompleteResponse searchMatchAutocomplete(String query, int limit) {
        if (query == null || query.isBlank()) {
            return SearchAutocompleteResponse.empty();
        }

        // 1. 입력 정규화 및 팀명 추출
        List<String> teamKeywords = extractTeamKeywords(query);

        if (teamKeywords.isEmpty()) {
            return SearchAutocompleteResponse.empty();
        }

        // 2. 각 키워드별로 Elasticsearch에서 팀 검색 (초성/한글/영문 지원)
        List<List<Team>> teamsByKeyword = new ArrayList<>();
        for (String keyword : teamKeywords) {
            List<Team> teams = findTeamsByKeyword(keyword);
            if (!teams.isEmpty()) {
                teamsByKeyword.add(teams);
            }
        }

        if (teamsByKeyword.isEmpty()) {
            return SearchAutocompleteResponse.empty();
        }

        // 3. 경기 검색
        List<Game> games;
        if (teamsByKeyword.size() >= 2) {
            // 두 팀 모두 포함된 경기 검색 (AND 조건)
            List<Long> team1Ids = teamsByKeyword.get(0).stream().map(Team::getId).toList();
            List<Long> team2Ids = teamsByKeyword.get(1).stream().map(Team::getId).toList();
            games = gameRepository.findRecentGamesByBothTeams(team1Ids, team2Ids, limit);
        } else {
            // 한 팀만 입력된 경우, 해당 팀이 포함된 경기 검색
            List<Long> teamIds = teamsByKeyword.get(0).stream().map(Team::getId).toList();
            games = gameRepository.findRecentGamesByTeamIds(teamIds, limit);
        }

        if (games.isEmpty()) {
            return SearchAutocompleteResponse.empty();
        }

        // 4. 경기 정보를 DTO로 변환
        List<MatchSuggestionDto> suggestions = convertToSuggestions(games, limit);

        return SearchAutocompleteResponse.of(suggestions);
    }

    /**
     * 검색어에서 팀명 키워드 추출
     * "T1 vs Geng" -> ["T1", "Geng"]
     * "T1vsGeng" -> ["T1", "Geng"]
     * "T1 Geng" -> ["T1", "Geng"]
     */
    private List<String> extractTeamKeywords(String query) {
        String normalized = query.toLowerCase().trim();

        // "vs", "vs.", "-", "대" 등으로 분리 시도
        String[] parts = normalized.split("\\s*(vs\\.?|vs|대|-|\\s)\\s*");

        List<String> keywords = new ArrayList<>();
        for (String part : parts) {
            String cleaned = part.trim();
            if (!cleaned.isEmpty() && cleaned.length() >= 2) {
                keywords.add(cleaned);
            }
        }

        return keywords;
    }

    /**
     * Elasticsearch를 활용한 팀 검색 (초성/한글/영문 지원)
     * ES에 데이터가 없으면 MySQL로 폴백
     */
    private List<Team> findTeamsByKeyword(String keyword) {
        try {
            // ES에서 검색
            List<SearchDocument> docs = searchDocumentRepository.searchByTypeAndKeyword("TEAM", keyword);

            if (!docs.isEmpty()) {
                // 정확도를 위해 가장 점수가 높은 상위 1개 팀만 사용
                // (edge_ngram으로 인해 유사도가 낮은 팀들도 검색될 수 있음)
                Long bestMatchTeamId = docs.get(0).getEntityId();
                return teamRepository.findAllById(List.of(bestMatchTeamId));
            }
        } catch (Exception e) {
            // ES 연결 실패 시 로그만 남기고 폴백
            log.warn("Elasticsearch 검색 실패, MySQL로 폴백: {}", e.getMessage());
        }

        // 폴백: MySQL에서 검색
        return teamRepository.findByNameContainingIgnoreCase(keyword);
    }

    /**
     * 게임 목록을 MatchSuggestionDto로 변환
     */
    private List<MatchSuggestionDto> convertToSuggestions(List<Game> games, int limit) {
        List<Long> gameIds = games.stream().map(Game::getId).toList();

        // N+1 방지: 참가자 정보 한 번에 조회
        Map<Long, List<GameParticipant>> participantsByGameId = gameParticipantRepository
                .findWithDetailsByGameIds(gameIds)
                .stream()
                .collect(Collectors.groupingBy(p -> p.getGame().getId()));

        List<MatchSuggestionDto> suggestions = new ArrayList<>();

        for (Game game : games) {
            List<GameParticipant> participants = participantsByGameId.get(game.getId());
            if (participants == null || participants.isEmpty())
                continue;

            // Blue/Red 팀 분리
            Map<String, List<GameParticipant>> bySide = participants.stream()
                    .collect(Collectors.groupingBy(GameParticipant::getSide));

            List<GameParticipant> blueTeam = bySide.get("Blue");
            List<GameParticipant> redTeam = bySide.get("Red");

            if (blueTeam == null || blueTeam.isEmpty() || redTeam == null || redTeam.isEmpty())
                continue;

            String blueTeamName = blueTeam.get(0).getTeam().getName();
            String blueTeamCode = blueTeam.get(0).getTeam().getCode();
            String blueTeamImageUrl = blueTeam.get(0).getTeam().getImageUrl();

            String redTeamName = redTeam.get(0).getTeam().getName();
            String redTeamCode = redTeam.get(0).getTeam().getCode();
            String redTeamImageUrl = redTeam.get(0).getTeam().getImageUrl();

            Boolean blueWin = blueTeam.get(0).getIsWin();

            suggestions.add(MatchSuggestionDto.of(
                    game.getId(),
                    blueTeamName,
                    blueTeamCode,
                    blueTeamImageUrl,
                    redTeamName,
                    redTeamCode,
                    redTeamImageUrl,
                    blueWin,
                    game.getLeague().getLeagueName(),
                    game.getActualGameStartTime(),
                    game.getPatch(),
                    game.getGameNumber()));

            if (suggestions.size() >= limit)
                break;
        }

        return suggestions;
    }
}
