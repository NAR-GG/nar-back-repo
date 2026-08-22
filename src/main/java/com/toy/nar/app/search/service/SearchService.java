package com.toy.nar.app.search.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Comparator;
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
import org.springframework.beans.factory.ObjectProvider;

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
    /**
     * ES 리포지토리. 검색이 꺼져 있거나(search.elasticsearch.enabled=false) ES 가 죽어 있으면
     * 빈이 없거나 생성에 실패한다. ObjectProvider 라 주입 시점에는 아무것도 건드리지 않고,
     * 실제 검색 때만 꺼내 쓴다 — 실패하면 아래 MySQL 폴백으로 넘어간다.
     */
    private final ObjectProvider<SearchDocumentRepository> searchDocumentRepository;

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
        // 매치 그룹핑을 위해 limit보다 더 많은 게임을 조회해야 함 (Bo3, Bo5 고려)
        // 안전하게 limit * 5 게임 조회 후 그룹핑 과정에서 limit 개수만큼 자름
        int gameSearchLimit = limit * 5;

        List<Game> games;
        if (teamsByKeyword.size() >= 2) {
            // 두 팀 모두 포함된 경기 검색 (AND 조건)
            List<Long> team1Ids = teamsByKeyword.get(0).stream().map(Team::getId).toList();
            List<Long> team2Ids = teamsByKeyword.get(1).stream().map(Team::getId).toList();
            games = gameRepository.findRecentGamesByBothTeams(team1Ids, team2Ids, gameSearchLimit);
        } else {
            // 한 팀만 입력된 경우, 해당 팀이 포함된 경기 검색
            List<Long> teamIds = teamsByKeyword.get(0).stream().map(Team::getId).toList();
            games = gameRepository.findRecentGamesByTeamIds(teamIds, gameSearchLimit);
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
     * ES에 데이터가 없으면 MySQL로 폴백 (team_code가 있는 팀만)
     */
    private List<Team> findTeamsByKeyword(String keyword) {
        log.info("[DEBUG] findTeamsByKeyword called with keyword: '{}'", keyword);
        try {
            // ES에서 검색
            List<SearchDocument> docs = searchDocumentRepository.getObject().searchByTypeAndKeyword("TEAM", keyword);

            if (!docs.isEmpty()) {
                // 정확도를 위해 가장 점수가 높은 상위 1개 팀만 사용
                // (edge_ngram으로 인해 유사도가 낮은 팀들도 검색될 수 있음)
                Long bestMatchTeamId = docs.get(0).getEntityId();
                log.info("[DEBUG] ES found team: entityId={}, name={}", bestMatchTeamId, docs.get(0).getName());
                return teamRepository.findAllById(List.of(bestMatchTeamId));
            }
            log.info("[DEBUG] ES returned empty results for keyword: '{}'", keyword);
        } catch (Exception e) {
            // ES 연결 실패 시 로그만 남기고 폴백
            log.warn("Elasticsearch 검색 실패, MySQL로 폴백: {}", e.getMessage());
        }

        // 폴백: MySQL에서 검색 (team_code가 있는 주요 리그 팀만)
        // 1. teamCode 정확 매칭 시도
        List<Team> codeMatch = teamRepository.findByCodeIgnoreCase(keyword);
        log.info("[DEBUG] MySQL codeMatch for '{}': {} results -> {}", keyword, codeMatch.size(),
                codeMatch.stream().map(t -> t.getName() + "(" + t.getCode() + ")").toList());
        if (!codeMatch.isEmpty()) {
            return codeMatch;
        }
        // 2. 이름 포함 검색 (team_code 있는 팀만)
        List<Team> nameMatch = teamRepository.findByNameContainingIgnoreCaseAndCodeIsNotNull(keyword);
        log.info("[DEBUG] MySQL nameMatch for '{}': {} results -> {}", keyword, nameMatch.size(),
                nameMatch.stream().map(t -> t.getName() + "(" + t.getCode() + ")").toList());
        return nameMatch;
    }

    /**
     * 게임 목록을 MatchSuggestionDto로 변환
     */
    /**
     * 게임 목록을 MatchSuggestionDto로 변환 (같은 매치의 게임들을 하나로 그룹핑)
     */
    private List<MatchSuggestionDto> convertToSuggestions(List<Game> games, int limit) {
        List<Long> gameIds = games.stream().map(Game::getId).toList();

        // N+1 방지: 참가자 정보 한 번에 조회
        Map<Long, List<GameParticipant>> participantsByGameId = gameParticipantRepository
                .findWithDetailsByGameIds(gameIds)
                .stream()
                .collect(Collectors.groupingBy(p -> p.getGame().getId()));

        // 1. 날짜 + 참여팀 기준으로 게임 그룹핑 (LinkedHashMap으로 순서 유지)
        // 키 형식: "YYYY-MM-DD_TeamCode1_TeamCode2"
        Map<String, List<Game>> matchGroups = games.stream()
                .collect(Collectors.groupingBy(
                        game -> {
                            String date = (game.getScheduledGameStartTime() != null
                                    ? game.getScheduledGameStartTime()
                                    : game.getActualGameStartTime()).toLocalDate().toString();

                            List<GameParticipant> participants = participantsByGameId.get(game.getId());
                            if (participants == null || participants.isEmpty()) {
                                return date + "_" + game.getId(); // 참여자 없으면 게임 ID로 분리
                            }

                            String teams = participants.stream()
                                    .map(GameParticipant::getTeam)
                                    .filter(Objects::nonNull)
                                    .map(Team::getCode)
                                    .filter(Objects::nonNull)
                                    .sorted()
                                    .collect(Collectors.joining("_"));

                            return date + "_" + teams;
                        },
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<MatchSuggestionDto> suggestions = new ArrayList<>();

        for (List<Game> matchGames : matchGroups.values()) {
            if (matchGames.isEmpty())
                continue;

            // gameNumber 기준 오름차순 정렬 (Null safe)
            matchGames
                    .sort(Comparator.comparing(Game::getGameNumber, Comparator.nullsFirst(Comparator.naturalOrder())));

            Game firstGame = matchGames.get(0);
            List<GameParticipant> firstGameParticipants = participantsByGameId.get(firstGame.getId());
            if (firstGameParticipants == null || firstGameParticipants.isEmpty()) {
                continue;
            }

            // game1의 Blue 팀을 Team A, Red 팀을 Team B로 정의 (기준점)
            Map<String, List<GameParticipant>> bySide = firstGameParticipants.stream()
                    .collect(Collectors.groupingBy(GameParticipant::getSide));

            List<GameParticipant> blueTeamParticipants = bySide.get("Blue");
            List<GameParticipant> redTeamParticipants = bySide.get("Red");

            if (blueTeamParticipants == null || blueTeamParticipants.isEmpty() ||
                    redTeamParticipants == null || redTeamParticipants.isEmpty()) {
                continue;
            }

            Team teamAEntity = blueTeamParticipants.get(0).getTeam();
            Team teamBEntity = redTeamParticipants.get(0).getTeam();

            if (teamAEntity == null || teamBEntity == null)
                continue;

            String teamAName = teamAEntity.getName();

            // 점수 계산
            int teamAScore = 0;
            int teamBScore = 0;

            for (Game game : matchGames) {
                List<GameParticipant> participants = participantsByGameId.get(game.getId());
                if (participants == null)
                    continue;

                // 해당 게임에서 Team A(이름 기준)가 이겼는지 확인
                boolean teamAWonThisGame = participants.stream()
                        .filter(p -> p.getTeam() != null && Objects.equals(p.getTeam().getName(), teamAName))
                        .findFirst()
                        .map(p -> Boolean.TRUE.equals(p.getIsWin()))
                        .orElse(false);

                if (teamAWonThisGame) {
                    teamAScore++;
                } else {
                    teamBScore++;
                }
            }

            // 승자 결정 (현재 스코어 기준)
            String winnerName = (teamAScore > teamBScore) ? teamAEntity.getName() : teamBEntity.getName();
            boolean isBlueWin = winnerName.equals(teamAName);

            suggestions.add(MatchSuggestionDto.of(
                    firstGame.getId(),
                    teamAEntity.getName(),
                    teamAEntity.getCode(),
                    teamAEntity.getImageUrl(),
                    teamAScore,
                    teamBEntity.getName(),
                    teamBEntity.getCode(),
                    teamBEntity.getImageUrl(),
                    teamBScore,
                    isBlueWin,
                    firstGame.getLeague().getLeagueName(),
                    firstGame.getScheduledGameStartTime() != null
                            ? firstGame.getScheduledGameStartTime()
                            : firstGame.getActualGameStartTime(),
                    firstGame.getPatch()));

            if (suggestions.size() >= limit)
                break;
        }

        return suggestions;
    }
}
