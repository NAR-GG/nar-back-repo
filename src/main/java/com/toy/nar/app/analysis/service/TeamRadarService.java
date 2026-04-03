package com.toy.nar.app.analysis.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
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

/**
 * 팀 레이더 차트 통계 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamRadarService {

	private final GameTeamStatRepository gameTeamStatRepository;
	private final GameParticipantRepository gameParticipantRepository;

	public TeamRadarResponse getTeamRadarStats(
			Long teamId,
			String league,
			Integer year,
			String split,
			String patch,
			String side) {
		TeamAnalysisFilter filter = TeamAnalysisFilter.from(league, year, split, patch, side);

		List<GameTeamStat> allStats = gameTeamStatRepository.findByFilter(
				filter.leagueName(),
				filter.year(),
				filter.split(),
				filter.patch(),
				filter.side());
		if (allStats.isEmpty()) {
			throw new IllegalArgumentException(
					"No games found for league " + filter.leagueName() + " in year " + filter.year());
		}

		List<GameParticipant> allParticipants = gameParticipantRepository.findByFilterWithStats(
				filter.leagueName(),
				filter.year(),
				filter.split(),
				filter.patch(),
				filter.side());

		List<RadarMetrics> teamMetrics = buildTeamMetrics(allStats, allParticipants, filter.year());
		RadarMetrics teamMetric = teamMetrics.stream()
				.filter(metric -> metric.teamId() != null && metric.teamId().equals(teamId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"No games found for team " + teamId + " in league " + filter.leagueName()
								+ " for year " + filter.year()));

		GoldScoreContext goldScoreContext = GoldScoreContext.from(teamMetrics);
		RadarMetrics leagueAverage = calculateLeagueAverage(allStats, teamMetrics, filter.leagueName(), filter.year());

		return TeamRadarResponse.builder()
				.stats(toDto(teamMetric, goldScoreContext))
				.leagueAverage(toDto(leagueAverage, goldScoreContext))
				.build();
	}

	private List<RadarMetrics> buildTeamMetrics(
			List<GameTeamStat> allStats,
			List<GameParticipant> allParticipants,
			int year) {
		Map<Long, List<GameTeamStat>> statsByTeam = allStats.stream()
				.collect(Collectors.groupingBy(gts -> gts.getTeam().getId()));
		Map<Long, List<GameParticipant>> participantsByTeam = allParticipants.stream()
				.collect(Collectors.groupingBy(gp -> gp.getTeam().getId()));

		return statsByTeam.entrySet().stream()
				.map(entry -> calculateTeamMetrics(
						entry.getValue(),
						participantsByTeam.getOrDefault(entry.getKey(), List.of()),
						year))
				.toList();
	}

	private RadarMetrics calculateTeamMetrics(
			List<GameTeamStat> teamStats,
			List<GameParticipant> participants,
			int year) {
		Team team = teamStats.get(0).getTeam();
		int gamesPlayed = teamStats.size();

		long wins = teamStats.stream().filter(s -> s.getResult() == 1).count();
		double winRate = gamesPlayed > 0 ? (double) wins / gamesPlayed : 0;

		Map<Long, List<GameParticipant>> participantsByGame = participants.stream()
				.collect(Collectors.groupingBy(gp -> gp.getGame().getId()));

		double goldDiffAt10 = calculateAvgGoldDiffAtTime(participantsByGame, 10);
		double goldDiffAt15 = calculateAvgGoldDiffAtTime(participantsByGame, 15);
		double goldDiffAt20 = calculateAvgGoldDiffAtTime(participantsByGame, 20);
		double goldDiffAt25 = calculateAvgGoldDiffAtTime(participantsByGame, 25);

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

		double dragonsPerGame = teamStats.stream()
				.mapToInt(s -> s.getDragons() != null ? s.getDragons() : 0)
				.average().orElse(0);

		double towersKilledAvg = teamStats.stream()
				.mapToInt(s -> s.getTowers() != null ? s.getTowers() : 0)
				.average().orElse(0);

		double towersLostAvg = teamStats.stream()
				.mapToInt(s -> s.getOppTowers() != null ? s.getOppTowers() : 0)
				.average().orElse(0);

		int totalVoidGrubs = teamStats.stream()
				.mapToInt(s -> s.getVoidGrubs() != null ? s.getVoidGrubs() : 0)
				.sum();
		int totalOppVoidGrubs = teamStats.stream()
				.mapToInt(s -> s.getOppVoidGrubs() != null ? s.getOppVoidGrubs() : 0)
				.sum();
		double voidGrubsRate = (totalVoidGrubs + totalOppVoidGrubs) > 0
				? (double) totalVoidGrubs / (totalVoidGrubs + totalOppVoidGrubs)
				: 0.5;

		double ckpm = teamStats.stream()
				.map(GameTeamStat::getGame)
				.mapToDouble(g -> g.getCkpm() != null ? g.getCkpm() : 0)
				.average().orElse(0);

		double gspd = teamStats.stream()
				.mapToDouble(s -> s.getGspd() != null ? s.getGspd() : 0)
				.average().orElse(0);

		double earlyGameRating = (firstBloodRate + firstTowerRate + firstHeraldRate) / 3;
		double midLateRating = (firstBaronRate + dragonsPerGame / 5.0 + winRate) / 3;

		double avgKills = teamStats.stream()
				.mapToInt(s -> s.getTeamKills() != null ? s.getTeamKills() : 0)
				.average().orElse(0);
		double pointsPerGame = avgKills + dragonsPerGame * 2 + towersKilledAvg;

		return new RadarMetrics(
				team.getId(),
				team.getName(),
				gamesPlayed,
				year,
				winRate,
				goldDiffAt10,
				goldDiffAt15,
				goldDiffAt20,
				goldDiffAt25,
				gspd,
				ckpm,
				firstBloodRate,
				firstTowerRate,
				firstThreeTowerRate,
				firstHeraldRate,
				firstDragonRate,
				firstBaronRate,
				dragonsPerGame,
				voidGrubsRate,
				towersKilledAvg,
				towersLostAvg,
				earlyGameRating,
				midLateRating,
				pointsPerGame);
	}

	private double calculateAvgGoldDiffAtTime(Map<Long, List<GameParticipant>> participantsByGame, int minutes) {
		if (participantsByGame.isEmpty()) {
			return 0;
		}

		double totalGoldDiff = 0;
		int gameCount = 0;

		for (List<GameParticipant> gameParticipants : participantsByGame.values()) {
			int teamGoldDiff = 0;
			for (GameParticipant gp : gameParticipants) {
				GamePlayerStat stat = gp.getStat();
				if (stat == null) {
					continue;
				}
				Integer goldDiff = switch (minutes) {
					case 10 -> stat.getGoldDiffAt10();
					case 15 -> stat.getGoldDiffAt15();
					case 20 -> stat.getGoldDiffAt20();
					case 25 -> stat.getGoldDiffAt25();
					default -> 0;
				};
				teamGoldDiff += goldDiff != null ? goldDiff : 0;
			}
			totalGoldDiff += teamGoldDiff;
			gameCount++;
		}

		return gameCount > 0 ? totalGoldDiff / gameCount : 0;
	}

	private RadarMetrics calculateLeagueAverage(
			List<GameTeamStat> allStats,
			Collection<RadarMetrics> teamMetrics,
			String leagueName,
			int year) {
		int teamGameCount = allStats.size();
		int uniqueGamesPlayed = (int) allStats.stream()
				.map(gts -> gts.getGame().getId())
				.distinct()
				.count();

		long wins = allStats.stream().filter(s -> s.getResult() == 1).count();
		double winRate = teamGameCount > 0 ? (double) wins / teamGameCount : 0;

		double firstBloodRate = allStats.stream().filter(GameTeamStat::isFirstBlood).count()
				/ (double) teamGameCount;
		double firstTowerRate = allStats.stream().filter(GameTeamStat::isFirstTower).count()
				/ (double) teamGameCount;
		double firstThreeTowerRate = allStats.stream().filter(GameTeamStat::isFirstToThreeTowers).count()
				/ (double) teamGameCount;
		double firstHeraldRate = allStats.stream().filter(GameTeamStat::isFirstHerald).count()
				/ (double) teamGameCount;
		double firstDragonRate = allStats.stream().filter(GameTeamStat::isFirstDragon).count()
				/ (double) teamGameCount;
		double firstBaronRate = allStats.stream().filter(GameTeamStat::isFirstBaron).count()
				/ (double) teamGameCount;

		double dragonsPerGame = allStats.stream()
				.mapToInt(s -> s.getDragons() != null ? s.getDragons() : 0)
				.average().orElse(0);

		double towersKilledAvg = allStats.stream()
				.mapToInt(s -> s.getTowers() != null ? s.getTowers() : 0)
				.average().orElse(0);

		double towersLostAvg = allStats.stream()
				.mapToInt(s -> s.getOppTowers() != null ? s.getOppTowers() : 0)
				.average().orElse(0);

		int totalVoidGrubs = allStats.stream()
				.mapToInt(s -> s.getVoidGrubs() != null ? s.getVoidGrubs() : 0)
				.sum();
		int totalOppVoidGrubs = allStats.stream()
				.mapToInt(s -> s.getOppVoidGrubs() != null ? s.getOppVoidGrubs() : 0)
				.sum();
		double voidGrubsRate = (totalVoidGrubs + totalOppVoidGrubs) > 0
				? (double) totalVoidGrubs / (totalVoidGrubs + totalOppVoidGrubs)
				: 0.5;

		double ckpm = allStats.stream()
				.map(GameTeamStat::getGame)
				.mapToDouble(g -> g.getCkpm() != null ? g.getCkpm() : 0)
				.average().orElse(0);

		double gspd = allStats.stream()
				.mapToDouble(s -> s.getGspd() != null ? s.getGspd() : 0)
				.average().orElse(0);

		double earlyGameRating = (firstBloodRate + firstTowerRate + firstHeraldRate) / 3;
		double midLateRating = (firstBaronRate + dragonsPerGame / 5.0 + winRate) / 3;

		double avgKills = allStats.stream()
				.mapToInt(s -> s.getTeamKills() != null ? s.getTeamKills() : 0)
				.average().orElse(0);
		double pointsPerGame = avgKills + dragonsPerGame * 2 + towersKilledAvg;

		return new RadarMetrics(
				null,
				leagueName + " Average",
				uniqueGamesPlayed,
				year,
				winRate,
				average(teamMetrics, RadarMetrics::goldDiffAt10),
				average(teamMetrics, RadarMetrics::goldDiffAt15),
				average(teamMetrics, RadarMetrics::goldDiffAt20),
				average(teamMetrics, RadarMetrics::goldDiffAt25),
				gspd,
				ckpm,
				firstBloodRate,
				firstTowerRate,
				firstThreeTowerRate,
				firstHeraldRate,
				firstDragonRate,
				firstBaronRate,
				dragonsPerGame,
				voidGrubsRate,
				towersKilledAvg,
				towersLostAvg,
				earlyGameRating,
				midLateRating,
				pointsPerGame);
	}

	private TeamRadarStatsDto toDto(RadarMetrics metrics, GoldScoreContext scoreContext) {
		double goldDiffAt10Score = round(scoreContext.scoreAt10(metrics.goldDiffAt10()), 1);
		double goldDiffAt15Score = round(scoreContext.scoreAt15(metrics.goldDiffAt15()), 1);
		double goldDiffAt20Score = round(scoreContext.scoreAt20(metrics.goldDiffAt20()), 1);
		double goldDiffAt25Score = round(scoreContext.scoreAt25(metrics.goldDiffAt25()), 1);

		return TeamRadarStatsDto.builder()
				.teamId(metrics.teamId())
				.teamName(metrics.teamName())
				.gamesPlayed(metrics.gamesPlayed())
				.year(metrics.year())
				.winRate(round(metrics.winRate(), 3))
				// Temporary frontend compatibility: legacy goldDiffAt* consumers still expect
				// the radar axis value from the raw field name.
				.goldDiffAt10(goldDiffAt10Score)
				.goldDiffAt10Score(goldDiffAt10Score)
				.goldDiffAt15(goldDiffAt15Score)
				.goldDiffAt15Score(goldDiffAt15Score)
				.goldDiffAt20(goldDiffAt20Score)
				.goldDiffAt20Score(goldDiffAt20Score)
				.goldDiffAt25(goldDiffAt25Score)
				.goldDiffAt25Score(goldDiffAt25Score)
				.gspd(round(metrics.gspd(), 3))
				.ckpm(round(metrics.ckpm(), 2))
				.firstBloodRate(round(metrics.firstBloodRate(), 3))
				.firstTowerRate(round(metrics.firstTowerRate(), 3))
				.firstThreeTowerRate(round(metrics.firstThreeTowerRate(), 3))
				.firstHeraldRate(round(metrics.firstHeraldRate(), 3))
				.firstDragonRate(round(metrics.firstDragonRate(), 3))
				.firstBaronRate(round(metrics.firstBaronRate(), 3))
				.dragonsPerGame(round(metrics.dragonsPerGame(), 2))
				.voidGrubsRate(round(metrics.voidGrubsRate(), 3))
				.towersKilledAvg(round(metrics.towersKilledAvg(), 2))
				.towersLostAvg(round(metrics.towersLostAvg(), 2))
				.earlyGameRating(round(metrics.earlyGameRating(), 3))
				.midLateRating(round(metrics.midLateRating(), 3))
				.pointsPerGame(round(metrics.pointsPerGame(), 2))
				.build();
	}

	private double average(Collection<RadarMetrics> metrics, ToDoubleFunction<RadarMetrics> extractor) {
		return metrics.stream().mapToDouble(extractor).average().orElse(0);
	}

	private double round(double value, int places) {
		double scale = Math.pow(10, places);
		return Math.round(value * scale) / scale;
	}

	private record RadarMetrics(
			Long teamId,
			String teamName,
			int gamesPlayed,
			int year,
			double winRate,
			double goldDiffAt10,
			double goldDiffAt15,
			double goldDiffAt20,
			double goldDiffAt25,
			double gspd,
			double ckpm,
			double firstBloodRate,
			double firstTowerRate,
			double firstThreeTowerRate,
			double firstHeraldRate,
			double firstDragonRate,
			double firstBaronRate,
			double dragonsPerGame,
			double voidGrubsRate,
			double towersKilledAvg,
			double towersLostAvg,
			double earlyGameRating,
			double midLateRating,
			double pointsPerGame) {
	}

	private record GoldScoreContext(
			double avg10,
			double maxAbs10,
			double avg15,
			double maxAbs15,
			double avg20,
			double maxAbs20,
			double avg25,
			double maxAbs25) {

		private static GoldScoreContext from(Collection<RadarMetrics> metrics) {
			double avg10 = metrics.stream().mapToDouble(RadarMetrics::goldDiffAt10).average().orElse(0);
			double avg15 = metrics.stream().mapToDouble(RadarMetrics::goldDiffAt15).average().orElse(0);
			double avg20 = metrics.stream().mapToDouble(RadarMetrics::goldDiffAt20).average().orElse(0);
			double avg25 = metrics.stream().mapToDouble(RadarMetrics::goldDiffAt25).average().orElse(0);

			return new GoldScoreContext(
					avg10,
					maxAbsDeviation(metrics, avg10, RadarMetrics::goldDiffAt10),
					avg15,
					maxAbsDeviation(metrics, avg15, RadarMetrics::goldDiffAt15),
					avg20,
					maxAbsDeviation(metrics, avg20, RadarMetrics::goldDiffAt20),
					avg25,
					maxAbsDeviation(metrics, avg25, RadarMetrics::goldDiffAt25));
		}

		private static double maxAbsDeviation(
				Collection<RadarMetrics> metrics,
				double average,
				ToDoubleFunction<RadarMetrics> extractor) {
			return metrics.stream()
					.mapToDouble(metric -> Math.abs(extractor.applyAsDouble(metric) - average))
					.max()
					.orElse(0);
		}

		private double scoreAt10(double raw) {
			return score(raw, avg10, maxAbs10);
		}

		private double scoreAt15(double raw) {
			return score(raw, avg15, maxAbs15);
		}

		private double scoreAt20(double raw) {
			return score(raw, avg20, maxAbs20);
		}

		private double scoreAt25(double raw) {
			return score(raw, avg25, maxAbs25);
		}

		private double score(double raw, double average, double maxAbsDeviation) {
			if (maxAbsDeviation == 0) {
				return 50;
			}
			double normalized = 50 + 50 * (raw - average) / maxAbsDeviation;
			return Math.max(0, Math.min(100, normalized));
		}
	}
}
