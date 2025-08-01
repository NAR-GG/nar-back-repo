package com.toy.nar.app.record.dto;

import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.participant.entity.GamePlayerStat;
import com.toy.nar.domain.participant.entity.GameTeamStat;

public record PlayerRecordDto(
	// === GameParticipant 정보 ===
	long participantid, String side, String position, String playername, String teamname, String champion, int result,

	// === GameTeamStat 정보 ===
	int teamkills, int teamdeaths, boolean firstblood, boolean firstdragon, int dragons, int oppDragons,
	int elementaldrakes, int oppElementaldrakes, int infernals, int mountains, int clouds, int oceans,
	int chemtechs, int hextechs, int elders, int oppElders, boolean firstherald, int heralds, int oppHeralds,
	int voidGrubs, int oppVoidGrubs, boolean firstbaron, int barons, int oppBarons, boolean firsttower,
	int towers, int oppTowers, int turretPlates, int oppTurretPlates, int inhibitors, int oppInhibitors,
	double gspd, // gspd 필드 추가

	// === GamePlayerStat 정보 ===
	int kills, int deaths, int assists, int doubleKills, int tripleKills, int quadraKills, int pentaKills,
	boolean isFirstBloodKill, boolean isFirstBloodAssist, boolean isFirstBloodVictim,
	int damageToChampions, double dpm, double damageShare, double damageTakenPerMinute, double damageMitigatedPerMinute,
	int wardsPlaced, double wpm, int wardsKilled, double wcpm, int controlWardsBought, double visionScore, double vspm,
	int totalGold, int earnedGold, double earnedGpm, double earnedGoldShare, int goldSpent,
	int totalCs, double cspm, int minionKills, int monsterKills,
	// 10분
	int goldAt10, int oppGoldAt10, int goldDiffAt10, int xpAt10, int oppXpAt10, int xpDiffAt10,
	int csAt10, int oppCsAt10, int csdiffAt10, int killsAt10, int assistsAt10, int deathsAt10,
	int oppKillsAt10, int oppAssistsAt10, int oppDeathsAt10,
	// 15분
	int goldAt15, int oppGoldAt15, int goldDiffAt15, int xpAt15, int oppXpAt15, int xpDiffAt15,
	int csAt15, int oppCsAt15, int csdiffAt15, int killsAt15, int assistsAt15, int deathsAt15,
	int oppKillsAt15, int oppAssistsAt15, int oppDeathsAt15,
	// 20분
	int goldAt20, int oppGoldAt20, int goldDiffAt20, int xpAt20, int oppXpAt20, int xpDiffAt20,
	int csAt20, int oppCsAt20, int csdiffAt20, int killsAt20, int assistsAt20, int deathsAt20,
	int oppKillsAt20, int oppAssistsAt20, int oppDeathsAt20,
	// 25분
	int goldAt25, int oppGoldAt25, int goldDiffAt25, int xpAt25, int oppXpAt25, int xpDiffAt25,
	int csAt25, int oppCsAt25, int csdiffAt25, int killsAt25, int assistsAt25, int deathsAt25,
	int oppKillsAt25, int oppAssistsAt25, int oppDeathsAt25
) {
	/**
	 * 엔티티 객체들로부터 PlayerRecordDto를 생성하는 정적 팩토리 메소드
	 */
	public static PlayerRecordDto from(GameParticipant p, GameTeamStat teamStat) {
		GamePlayerStat playerStat = p.getStat();

		// playerStat이 null일 경우를 대비한 방어 코드
		if (playerStat == null) {
			// 통계 정보가 없는 경우, 기본값으로 DTO를 생성하거나 예외를 던질 수 있습니다.
			// 여기서는 기본값으로 생성합니다.
			playerStat = GamePlayerStat.builder().build(); // Lombok Builder는 필드를 null/0으로 초기화
		}

		return new PlayerRecordDto(
			// === GameParticipant 정보 ===
			p.getId(), p.getSide(), p.getPosition(), p.getPlayer().getName(), p.getTeam().getName(), p.getChampion().getChampionNameEn(), p.getIsWin() ? 1 : 0,

			// === GameTeamStat 정보 ===
			safeInt(teamStat.getTeamKills()), safeInt(teamStat.getTeamDeaths()), teamStat.isFirstBlood(), teamStat.isFirstDragon(),
			safeInt(teamStat.getDragons()), safeInt(teamStat.getOppDragons()), safeInt(teamStat.getElementaldrakes()),
			safeInt(teamStat.getOppElementaldrakes()), safeInt(teamStat.getInfernals()), safeInt(teamStat.getMountains()),
			safeInt(teamStat.getClouds()), safeInt(teamStat.getOceans()), safeInt(teamStat.getChemtechs()),
			safeInt(teamStat.getHextechs()), safeInt(teamStat.getElders()), safeInt(teamStat.getOppElders()),
			teamStat.isFirstHerald(), safeInt(teamStat.getHeralds()), safeInt(teamStat.getOppHeralds()),
			safeInt(teamStat.getVoidGrubs()), safeInt(teamStat.getOppVoidGrubs()), teamStat.isFirstBaron(),
			safeInt(teamStat.getBarons()), safeInt(teamStat.getOppBarons()), teamStat.isFirstTower(),
			safeInt(teamStat.getTowers()), safeInt(teamStat.getOppTowers()), safeInt(teamStat.getTurretPlates()),
			safeInt(teamStat.getOppTurretPlates()), safeInt(teamStat.getInhibitors()), safeInt(teamStat.getOppInhibitors()),
			safeDouble(teamStat.getGspd()),

			// === GamePlayerStat 정보 ===
			safeInt(playerStat.getKills()), safeInt(playerStat.getDeaths()), safeInt(playerStat.getAssists()),
			safeInt(playerStat.getDoubleKills()), safeInt(playerStat.getTripleKills()), safeInt(playerStat.getQuadraKills()), safeInt(playerStat.getPentaKills()),
			playerStat.isFirstBloodKill(), playerStat.isFirstBloodAssist(), playerStat.isFirstBloodVictim(),
			safeInt(playerStat.getDamageToChampions()), safeDouble(playerStat.getDpm()), safeDouble(playerStat.getDamageShare()),
			safeDouble(playerStat.getDamageTakenPerMinute()), safeDouble(playerStat.getDamageMitigatedPerMinute()),
			safeInt(playerStat.getWardsPlaced()), safeDouble(playerStat.getWpm()), safeInt(playerStat.getWardsKilled()),
			safeDouble(playerStat.getWcpm()), safeInt(playerStat.getControlWardsBought()), safeDouble(playerStat.getVisionScore()), safeDouble(playerStat.getVspm()),
			safeInt(playerStat.getTotalGold()), safeInt(playerStat.getEarnedGold()), safeDouble(playerStat.getEarnedGpm()),
			safeDouble(playerStat.getEarnedGoldShare()), safeInt(playerStat.getGoldSpent()),
			safeInt(playerStat.getTotalCs()), safeDouble(playerStat.getCspm()), safeInt(playerStat.getMinionKills()), safeInt(playerStat.getMonsterKills()),

			// 10분
			safeInt(playerStat.getGoldAt10()), safeInt(playerStat.getOppGoldAt10()), safeInt(playerStat.getGoldDiffAt10()),
			safeInt(playerStat.getXpAt10()), safeInt(playerStat.getOppXpAt10()), safeInt(playerStat.getXpDiffAt10()),
			safeInt(playerStat.getCsAt10()), safeInt(playerStat.getOppCsAt10()), safeInt(playerStat.getCsdiffAt10()),
			safeInt(playerStat.getKillsAt10()), safeInt(playerStat.getAssistsAt10()), safeInt(playerStat.getDeathsAt10()),
			safeInt(playerStat.getOppKillsAt10()), safeInt(playerStat.getOppAssistsAt10()), safeInt(playerStat.getOppDeathsAt10()),

			// 15분
			safeInt(playerStat.getGoldAt15()), safeInt(playerStat.getOppGoldAt15()), safeInt(playerStat.getGoldDiffAt15()),
			safeInt(playerStat.getXpAt15()), safeInt(playerStat.getOppXpAt15()), safeInt(playerStat.getXpDiffAt15()),
			safeInt(playerStat.getCsAt15()), safeInt(playerStat.getOppCsAt15()), safeInt(playerStat.getCsdiffAt15()),
			safeInt(playerStat.getKillsAt15()), safeInt(playerStat.getAssistsAt15()), safeInt(playerStat.getDeathsAt15()),
			safeInt(playerStat.getOppKillsAt15()), safeInt(playerStat.getOppAssistsAt15()), safeInt(playerStat.getOppDeathsAt15()),

			// 20분
			safeInt(playerStat.getGoldAt20()), safeInt(playerStat.getOppGoldAt20()), safeInt(playerStat.getGoldDiffAt20()),
			safeInt(playerStat.getXpAt20()), safeInt(playerStat.getOppXpAt20()), safeInt(playerStat.getXpDiffAt20()),
			safeInt(playerStat.getCsAt20()), safeInt(playerStat.getOppCsAt20()), safeInt(playerStat.getCsdiffAt20()),
			safeInt(playerStat.getKillsAt20()), safeInt(playerStat.getAssistsAt20()), safeInt(playerStat.getDeathsAt20()),
			safeInt(playerStat.getOppKillsAt20()), safeInt(playerStat.getOppAssistsAt20()), safeInt(playerStat.getOppDeathsAt20()),

			// 25분
			safeInt(playerStat.getGoldAt25()), safeInt(playerStat.getOppGoldAt25()), safeInt(playerStat.getGoldDiffAt25()),
			safeInt(playerStat.getXpAt25()), safeInt(playerStat.getOppXpAt25()), safeInt(playerStat.getXpDiffAt25()),
			safeInt(playerStat.getCsAt25()), safeInt(playerStat.getOppCsAt25()), safeInt(playerStat.getCsdiffAt25()),
			safeInt(playerStat.getKillsAt25()), safeInt(playerStat.getAssistsAt25()), safeInt(playerStat.getDeathsAt25()),
			safeInt(playerStat.getOppKillsAt25()), safeInt(playerStat.getOppAssistsAt25()), safeInt(playerStat.getOppDeathsAt25())
		);
	}

	/**
	 * Integer가 null일 경우 0을 반환하는 헬퍼 메소드
	 */
	private static int safeInt(Integer value) {
		return value != null ? value : 0;
	}

	/**
	 * Double이 null일 경우 0.0을 반환하는 헬퍼 메소드
	 */
	private static double safeDouble(Double value) {
		return value != null ? value : 0.0;
	}
}