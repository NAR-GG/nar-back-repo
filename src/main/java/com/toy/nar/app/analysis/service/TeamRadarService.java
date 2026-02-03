package com.toy.nar.app.analysis.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.analysis.dto.TeamRadarResponse;
import com.toy.nar.app.analysis.dto.TeamRadarStatsDto;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.participant.entity.GamePlayerStat;
import com.toy.nar.domain.participant.entity.GameTeamStat;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 팀 레이더 차트 통계 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TeamRadarService {

        private final GameTeamStatRepository gameTeamStatRepository;
        private final GameParticipantRepository gameParticipantRepository;

        /**
         * 팀 레이더 통계 조회
         */
        public TeamRadarResponse getTeamRadarStats(Long teamId, int year) {
                List<GameTeamStat> teamStats = gameTeamStatRepository.findByTeamIdAndYear(teamId, year);

                if (teamStats.isEmpty()) {
                        throw new IllegalArgumentException("No games found for team " + teamId + " in year " + year);
                }

                // 골드차 계산을 위해 선수별 통계 조회
                List<GameParticipant> participants = gameParticipantRepository.findByTeamIdAndYearWithStats(teamId,
                                year);

                TeamRadarStatsDto stats = calculateTeamStats(teamStats, participants, year);
                TeamRadarStatsDto leagueAverage = calculateLeagueAverage(year);

                return TeamRadarResponse.builder()
                                .stats(stats)
                                .leagueAverage(leagueAverage)
                                .build();
        }

        /**
         * 팀 통계 계산
         */
        private TeamRadarStatsDto calculateTeamStats(List<GameTeamStat> teamStats,
                        List<GameParticipant> participants, int year) {
                Team team = teamStats.get(0).getTeam();
                int gamesPlayed = teamStats.size();

                // === 승률 ===
                long wins = teamStats.stream().filter(s -> s.getResult() == 1).count();
                double winRate = (double) wins / gamesPlayed;

                // === 시간대별 골드차 계산 (선수 5명 합산 후 경기당 평균) ===
                Map<Long, List<GameParticipant>> participantsByGame = participants.stream()
                                .collect(Collectors.groupingBy(gp -> gp.getGame().getId()));

                double goldDiffAt10 = calculateAvgGoldDiffAtTime(participantsByGame, 10);
                double goldDiffAt15 = calculateAvgGoldDiffAtTime(participantsByGame, 15);
                double goldDiffAt20 = calculateAvgGoldDiffAtTime(participantsByGame, 20);
                double goldDiffAt25 = calculateAvgGoldDiffAtTime(participantsByGame, 25);

                // === 선취 지표 ===
                double firstBloodRate = teamStats.stream().filter(GameTeamStat::isFirstBlood).count()
                                / (double) gamesPlayed;
                double firstTowerRate = teamStats.stream().filter(GameTeamStat::isFirstTower).count()
                                / (double) gamesPlayed;
                double firstThreeTowerRate = teamStats.stream().filter(GameTeamStat::isFirstToThreeTowers).count()
                                / (double) gamesPlayed;
                double firstHeraldRate = teamStats.stream().filter(GameTeamStat::isFirstHerald).count()
                                / (double) gamesPlayed;
                double firstDragonRate = teamStats.stream().filter(GameTeamStat::isFirstDragon).count()
                                / (double) gamesPlayed;
                double firstBaronRate = teamStats.stream().filter(GameTeamStat::isFirstBaron).count()
                                / (double) gamesPlayed;

                // === 오브젝트 평균 ===
                double dragonsPerGame = teamStats.stream()
                                .mapToInt(s -> s.getDragons() != null ? s.getDragons() : 0)
                                .average().orElse(0);

                double towersKilledAvg = teamStats.stream()
                                .mapToInt(s -> s.getTowers() != null ? s.getTowers() : 0)
                                .average().orElse(0);

                double towersLostAvg = teamStats.stream()
                                .mapToInt(s -> s.getOppTowers() != null ? s.getOppTowers() : 0)
                                .average().orElse(0);

                // === 공허유충 비율 ===
                int totalVoidGrubs = teamStats.stream()
                                .mapToInt(s -> (s.getVoidGrubs() != null ? s.getVoidGrubs() : 0))
                                .sum();
                int totalOppVoidGrubs = teamStats.stream()
                                .mapToInt(s -> (s.getOppVoidGrubs() != null ? s.getOppVoidGrubs() : 0))
                                .sum();
                double voidGrubsRate = (totalVoidGrubs + totalOppVoidGrubs) > 0
                                ? (double) totalVoidGrubs / (totalVoidGrubs + totalOppVoidGrubs)
                                : 0.5;

                // === CKPM (경기 평균) ===
                double ckpm = teamStats.stream()
                                .map(GameTeamStat::getGame)
                                .mapToDouble(g -> g.getCkpm() != null ? g.getCkpm() : 0)
                                .average().orElse(0);

                // === GSPD (Gold Slope Percent Difference) ===
                double gspd = teamStats.stream()
                                .mapToDouble(s -> s.getGspd() != null ? s.getGspd() : 0)
                                .average().orElse(0);

                // === 계산 레이팅 ===
                double earlyGameRating = (firstBloodRate + firstTowerRate + firstHeraldRate) / 3;
                double mlr = (firstBaronRate + dragonsPerGame / 5.0 + winRate) / 3;

                double avgKills = teamStats.stream()
                                .mapToInt(s -> s.getTeamKills() != null ? s.getTeamKills() : 0)
                                .average().orElse(0);
                double pointsPerGame = avgKills + dragonsPerGame * 2 + towersKilledAvg;

                return TeamRadarStatsDto.builder()
                                .teamId(team.getId())
                                .teamName(team.getName())
                                .gamesPlayed(gamesPlayed)
                                .year(year)
                                .winRate(round(winRate, 3))
                                .goldDiffAt10(round(goldDiffAt10, 0))
                                .goldDiffAt15(round(goldDiffAt15, 0))
                                .goldDiffAt20(round(goldDiffAt20, 0))
                                .goldDiffAt25(round(goldDiffAt25, 0))
                                .gspd(round(gspd, 3))
                                .ckpm(round(ckpm, 2))
                                .firstBloodRate(round(firstBloodRate, 3))
                                .firstTowerRate(round(firstTowerRate, 3))
                                .firstThreeTowerRate(round(firstThreeTowerRate, 3))
                                .firstHeraldRate(round(firstHeraldRate, 3))
                                .firstDragonRate(round(firstDragonRate, 3))
                                .firstBaronRate(round(firstBaronRate, 3))
                                .dragonsPerGame(round(dragonsPerGame, 2))
                                .voidGrubsRate(round(voidGrubsRate, 3))
                                .towersKilledAvg(round(towersKilledAvg, 2))
                                .towersLostAvg(round(towersLostAvg, 2))
                                .earlyGameRating(round(earlyGameRating, 3))
                                .midLateRating(round(mlr, 3))
                                .pointsPerGame(round(pointsPerGame, 2))
                                .build();
        }

        /**
         * 시간대별 골드차 평균 계산
         */
        private double calculateAvgGoldDiffAtTime(Map<Long, List<GameParticipant>> participantsByGame, int minutes) {
                if (participantsByGame.isEmpty())
                        return 0;

                double totalGoldDiff = 0;
                int gameCount = 0;

                for (List<GameParticipant> gameParticipants : participantsByGame.values()) {
                        int teamGoldDiff = 0;
                        for (GameParticipant gp : gameParticipants) {
                                GamePlayerStat stat = gp.getStat();
                                if (stat != null) {
                                        Integer goldDiff = switch (minutes) {
                                                case 10 -> stat.getGoldDiffAt10();
                                                case 15 -> stat.getGoldDiffAt15();
                                                case 20 -> stat.getGoldDiffAt20();
                                                case 25 -> stat.getGoldDiffAt25();
                                                default -> 0;
                                        };
                                        teamGoldDiff += goldDiff != null ? goldDiff : 0;
                                }
                        }
                        totalGoldDiff += teamGoldDiff;
                        gameCount++;
                }

                return gameCount > 0 ? totalGoldDiff / gameCount : 0;
        }

        /**
         * GDM (분당 골드차) 계산 - gol.gg 방식
         * 팀 earnedGold 합산 vs 상대팀 earnedGold 차이 / 경기시간(분)
         */
        private double calculateGoldDiffPerMin(Map<Long, List<GameParticipant>> participantsByGame,
                        List<GameTeamStat> teamStats) {
                if (participantsByGame.isEmpty())
                        return 0;

                double totalGdm = 0;
                int gameCount = 0;

                for (Map.Entry<Long, List<GameParticipant>> entry : participantsByGame.entrySet()) {
                        Long gameId = entry.getKey();
                        List<GameParticipant> gameParticipants = entry.getValue();

                        // 해당 경기의 게임 시간 찾기
                        GameTeamStat matchingStat = teamStats.stream()
                                        .filter(s -> s.getGame().getId().equals(gameId))
                                        .findFirst().orElse(null);

                        if (matchingStat == null)
                                continue;

                        int gameLengthSeconds = matchingStat.getGame().getGameLengthSeconds();
                        double gameLengthMinutes = gameLengthSeconds / 60.0;

                        // 팀 earnedGold 합산
                        int teamGold = 0;
                        for (GameParticipant gp : gameParticipants) {
                                GamePlayerStat stat = gp.getStat();
                                if (stat != null && stat.getEarnedGold() != null) {
                                        teamGold += stat.getEarnedGold();
                                }
                        }

                        // 상대팀 골드 = 총 경기 킬당 골드 추정 (간단히 GPR 활용)
                        // GPR = (팀골드 - 상대골드) / 상대골드 이므로
                        // 상대골드 = 팀골드 / (1 + GPR)
                        Double gpr = matchingStat.getGpr();
                        int oppGold;
                        if (gpr != null && gpr != -1) {
                                oppGold = (int) (teamGold / (1 + gpr));
                        } else {
                                // GPR 없으면 승리시 +10%, 패배시 -10% 추정
                                oppGold = matchingStat.getResult() == 1
                                                ? (int) (teamGold * 0.9)
                                                : (int) (teamGold * 1.1);
                        }

                        double goldDiff = teamGold - oppGold;
                        double gdm = goldDiff / gameLengthMinutes;
                        totalGdm += gdm;
                        gameCount++;
                }

                return gameCount > 0 ? totalGdm / gameCount : 0;
        }

        /**
         * LCK 리그 평균 계산 (비교용 점선)
         */
        private TeamRadarStatsDto calculateLeagueAverage(int year) {
                // LCK 리그만 필터링
                List<GameTeamStat> allStats = gameTeamStatRepository.findByYearAndLeagueName(year, "LCK");

                if (allStats.isEmpty()) {
                        return null;
                }

                int totalGames = allStats.size();

                long wins = allStats.stream().filter(s -> s.getResult() == 1).count();
                double winRate = (double) wins / totalGames;

                double firstBloodRate = allStats.stream().filter(GameTeamStat::isFirstBlood).count()
                                / (double) totalGames;
                double firstTowerRate = allStats.stream().filter(GameTeamStat::isFirstTower).count()
                                / (double) totalGames;
                double firstThreeTowerRate = allStats.stream().filter(GameTeamStat::isFirstToThreeTowers).count()
                                / (double) totalGames;
                double firstHeraldRate = allStats.stream().filter(GameTeamStat::isFirstHerald).count()
                                / (double) totalGames;
                double firstDragonRate = allStats.stream().filter(GameTeamStat::isFirstDragon).count()
                                / (double) totalGames;
                double firstBaronRate = allStats.stream().filter(GameTeamStat::isFirstBaron).count()
                                / (double) totalGames;

                double dragonsPerGame = allStats.stream()
                                .mapToInt(s -> s.getDragons() != null ? s.getDragons() : 0)
                                .average().orElse(0);

                double towersKilledAvg = allStats.stream()
                                .mapToInt(s -> s.getTowers() != null ? s.getTowers() : 0)
                                .average().orElse(0);

                double towersLostAvg = allStats.stream()
                                .mapToInt(s -> s.getOppTowers() != null ? s.getOppTowers() : 0)
                                .average().orElse(0);

                double ckpm = allStats.stream()
                                .map(GameTeamStat::getGame)
                                .mapToDouble(g -> g.getCkpm() != null ? g.getCkpm() : 0)
                                .average().orElse(0);

                double gspd = allStats.stream()
                                .mapToDouble(s -> s.getGspd() != null ? s.getGspd() : 0)
                                .average().orElse(0);

                double earlyGameRating = (firstBloodRate + firstTowerRate + firstHeraldRate) / 3;
                double mlr = (firstBaronRate + dragonsPerGame / 5.0 + winRate) / 3;

                double avgKills = allStats.stream()
                                .mapToInt(s -> s.getTeamKills() != null ? s.getTeamKills() : 0)
                                .average().orElse(0);
                double pointsPerGame = avgKills + dragonsPerGame * 2 + towersKilledAvg;

                return TeamRadarStatsDto.builder()
                                .teamId(null)
                                .teamName("LCK Average")
                                .gamesPlayed(totalGames / 2)
                                .year(year)
                                .winRate(round(winRate, 3))
                                .goldDiffAt10(0.0) // 리그 평균은 항상 0
                                .goldDiffAt15(0.0)
                                .goldDiffAt20(0.0)
                                .goldDiffAt25(0.0)
                                .gspd(round(gspd, 3))
                                .ckpm(round(ckpm, 2))
                                .firstBloodRate(round(firstBloodRate, 3))
                                .firstTowerRate(round(firstTowerRate, 3))
                                .firstThreeTowerRate(round(firstThreeTowerRate, 3))
                                .firstHeraldRate(round(firstHeraldRate, 3))
                                .firstDragonRate(round(firstDragonRate, 3))
                                .firstBaronRate(round(firstBaronRate, 3))
                                .dragonsPerGame(round(dragonsPerGame, 2))
                                .voidGrubsRate(0.5)
                                .towersKilledAvg(round(towersKilledAvg, 2))
                                .towersLostAvg(round(towersLostAvg, 2))
                                .earlyGameRating(round(earlyGameRating, 3))
                                .midLateRating(round(mlr, 3))
                                .pointsPerGame(round(pointsPerGame, 2))
                                .build();
        }

        private double round(double value, int places) {
                double scale = Math.pow(10, places);
                return Math.round(value * scale) / scale;
        }
}
