package com.toy.nar.app.analysis.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.analysis.dto.ChampionPickDto;
import com.toy.nar.app.analysis.dto.MostPickByPosition;
import com.toy.nar.app.analysis.dto.OpponentChampionDto;
import com.toy.nar.app.analysis.dto.TeamRankingFilterDto;
import com.toy.nar.app.analysis.dto.TeamRankingItemDto;
import com.toy.nar.app.analysis.dto.TeamRankingResponseDto;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.BanRepository;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 팀 랭킹 및 모스트 픽 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamRankingService {

    private final GameTeamStatRepository gameTeamStatRepository;
    private final GameParticipantRepository gameParticipantRepository;
    private final BanRepository banRepository;

    private static final int MAX_OPPONENTS = 3; // 상대 챔피언 최대 표시 수

    /**
     * 팀 랭킹 조회
     */
    public TeamRankingResponseDto getTeamRankings(TeamRankingFilterDto filter) {
        // 1. 팀별 승률/전적 조회
        List<Object[]> teamStats = gameTeamStatRepository.findTeamStatsByFilter(
                filter.getYear(),
                filter.getLeagueNames(),
                filter.getSplits(),
                filter.getPatch(),
                filter.getEffectiveSide());

        if (teamStats.isEmpty()) {
            return new TeamRankingResponseDto(List.of(), 0, filter);
        }

        // 2. 전체 게임 수 (밴률 계산용)
        long totalGamesCount = getTotalGamesCount(filter);

        // 3. 팀-포지션별 모스트 픽 조회
        List<Object[]> pickStats = gameParticipantRepository.findMostPicksByTeamAndPosition(
                filter.getYear(),
                filter.getLeagueNames(),
                filter.getSplits(),
                filter.getPatch(),
                filter.getEffectiveSide());

        // 4. 챔피언별 밴 횟수 조회
        Map<String, Long> banCounts = getBanCounts(filter);

        // 5. 상대 챔피언 매치업 조회
        List<Object[]> opponentStats = gameParticipantRepository.findOpponentMatchupsByTeamAndPosition(
                filter.getYear(),
                filter.getLeagueNames(),
                filter.getSplits(),
                filter.getPatch(),
                filter.getEffectiveSide());

        // 6. 팀별 모스트 픽 그룹핑
        Map<Long, Map<String, List<Object[]>>> picksByTeamAndPosition = groupPicksByTeamAndPosition(pickStats);

        // 7. 상대 챔피언 매치업 그룹핑 (teamId -> position -> myChampion -> List<opponent>)
        Map<Long, Map<String, Map<String, List<Object[]>>>> opponentsByTeamPositionChampion = groupOpponentMatchups(
                opponentStats);

        // 8. 랭킹 DTO 생성
        List<TeamRankingItemDto> rankings = new ArrayList<>();
        int rank = 1;

        for (Object[] stat : teamStats) {
            Long teamId = ((Number) stat[0]).longValue();
            String teamName = (String) stat[1];
            String teamCode = (String) stat[2];
            String imageUrl = (String) stat[3];
            long wins = ((Number) stat[4]).longValue();
            long totalGames = ((Number) stat[5]).longValue();
            long losses = totalGames - wins;
            double winRate = totalGames > 0 ? (double) wins / totalGames * 100 : 0;

            // 해당 팀의 포지션별 모스트 픽
            MostPickByPosition mostPicks = buildMostPicks(
                    picksByTeamAndPosition.getOrDefault(teamId, Map.of()),
                    banCounts,
                    totalGamesCount,
                    opponentsByTeamPositionChampion.getOrDefault(teamId, Map.of()));

            rankings.add(new TeamRankingItemDto(
                    rank++,
                    teamId,
                    teamName,
                    teamCode,
                    imageUrl,
                    Math.round(winRate * 10) / 10.0,
                    (int) wins,
                    (int) losses,
                    (int) totalGames,
                    mostPicks));
        }

        return new TeamRankingResponseDto(rankings, rankings.size(), filter);
    }

    /**
     * 전체 게임 수 조회 (밴률 계산용)
     */
    private long getTotalGamesCount(TeamRankingFilterDto filter) {
        return gameTeamStatRepository.countGamesByFilter(
                filter.getYear(),
                filter.getLeagueNames(),
                filter.getSplits(),
                filter.getPatch(),
                filter.getEffectiveSide());
    }

    /**
     * 챔피언별 밴 횟수 조회
     */
    private Map<String, Long> getBanCounts(TeamRankingFilterDto filter) {
        List<Object[]> banStats = banRepository.findBanCountsByFilter(
                filter.getYear(),
                filter.getLeagueNames(),
                filter.getSplits(),
                filter.getPatch());

        return banStats.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));
    }

    /**
     * 팀-포지션별 픽 데이터 그룹핑
     */
    private Map<Long, Map<String, List<Object[]>>> groupPicksByTeamAndPosition(List<Object[]> pickStats) {
        Map<Long, Map<String, List<Object[]>>> result = new HashMap<>();

        for (Object[] row : pickStats) {
            Long teamId = ((Number) row[0]).longValue();
            String position = (String) row[1];

            result.computeIfAbsent(teamId, k -> new HashMap<>())
                    .computeIfAbsent(position, k -> new ArrayList<>())
                    .add(row);
        }

        return result;
    }

    /**
     * 상대 챔피언 매치업 그룹핑
     */
    private Map<Long, Map<String, Map<String, List<Object[]>>>> groupOpponentMatchups(List<Object[]> opponentStats) {
        Map<Long, Map<String, Map<String, List<Object[]>>>> result = new HashMap<>();

        for (Object[] row : opponentStats) {
            Long teamId = ((Number) row[0]).longValue();
            String position = (String) row[1];
            String myChampion = (String) row[2];

            result.computeIfAbsent(teamId, k -> new HashMap<>())
                    .computeIfAbsent(position, k -> new HashMap<>())
                    .computeIfAbsent(myChampion, k -> new ArrayList<>())
                    .add(row);
        }

        return result;
    }

    /**
     * 포지션별 모스트 픽 DTO 생성
     */
    private MostPickByPosition buildMostPicks(
            Map<String, List<Object[]>> picksByPosition,
            Map<String, Long> banCounts,
            long totalGamesCount,
            Map<String, Map<String, List<Object[]>>> opponentsByPositionChampion) {

        return new MostPickByPosition(
                buildPositionPicks(picksByPosition.get("top"), banCounts, totalGamesCount,
                        opponentsByPositionChampion.getOrDefault("top", Map.of())),
                buildPositionPicks(picksByPosition.get("jng"), banCounts, totalGamesCount,
                        opponentsByPositionChampion.getOrDefault("jng", Map.of())),
                buildPositionPicks(picksByPosition.get("mid"), banCounts, totalGamesCount,
                        opponentsByPositionChampion.getOrDefault("mid", Map.of())),
                buildPositionPicks(picksByPosition.get("bot"), banCounts, totalGamesCount,
                        opponentsByPositionChampion.getOrDefault("bot", Map.of())),
                buildPositionPicks(picksByPosition.get("sup"), banCounts, totalGamesCount,
                        opponentsByPositionChampion.getOrDefault("sup", Map.of())));
    }

    /**
     * 특정 포지션의 모스트 픽 리스트 생성 (동률 포함)
     */
    private List<ChampionPickDto> buildPositionPicks(
            List<Object[]> positionPicks,
            Map<String, Long> banCounts,
            long totalGamesCount,
            Map<String, List<Object[]>> opponentsByChampion) {

        if (positionPicks == null || positionPicks.isEmpty()) {
            return List.of();
        }

        // 픽 횟수 기준 내림차순 정렬
        positionPicks.sort((a, b) -> Long.compare(
                ((Number) b[3]).longValue(),
                ((Number) a[3]).longValue()));

        // 최대 픽 횟수
        long maxPickCount = ((Number) positionPicks.get(0)[3]).longValue();

        // 동률인 챔피언들 모두 포함
        List<ChampionPickDto> result = new ArrayList<>();
        for (Object[] row : positionPicks) {
            long pickCount = ((Number) row[3]).longValue();
            if (pickCount < maxPickCount)
                break;

            String championName = (String) row[2];
            String championImageUrl = (String) row[4];
            long wins = ((Number) row[5]).longValue();
            double winRate = pickCount > 0 ? (double) wins / pickCount * 100 : 0;
            long banCount = banCounts.getOrDefault(championName, 0L);
            double banRate = totalGamesCount > 0 ? (double) banCount / totalGamesCount * 100 : 0;

            // 상대 챔피언 Top 3 추출
            List<OpponentChampionDto> topOpponents = buildTopOpponents(
                    opponentsByChampion.getOrDefault(championName, List.of()));

            result.add(new ChampionPickDto(
                    championName,
                    championImageUrl,
                    (int) pickCount,
                    Math.round(winRate * 10) / 10.0,
                    Math.round(banRate * 10) / 10.0,
                    topOpponents));
        }

        return result;
    }

    /**
     * 상대 챔피언 Top 3 DTO 생성
     */
    private List<OpponentChampionDto> buildTopOpponents(List<Object[]> opponentRows) {
        if (opponentRows == null || opponentRows.isEmpty()) {
            return List.of();
        }

        // 이미 match_count DESC로 정렬되어 있음
        return opponentRows.stream()
                .limit(MAX_OPPONENTS)
                .map(row -> {
                    String opponentChampion = (String) row[3];
                    String opponentImageUrl = (String) row[4];
                    long matchCount = ((Number) row[5]).longValue();
                    long wins = ((Number) row[6]).longValue();
                    double winRate = matchCount > 0 ? (double) wins / matchCount * 100 : 0;

                    return new OpponentChampionDto(
                            opponentChampion,
                            opponentImageUrl,
                            (int) matchCount,
                            Math.round(winRate * 10) / 10.0);
                })
                .toList();
    }
}
