package com.toy.nar.domain.participant.entity;

import com.toy.nar.app.data.ingestion.dto.GameDataCsvDto;
import com.toy.nar.domain.game.entity.GameParticipant;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GamePlayerStat {

	@Id
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "game_participant_id")
	private GameParticipant gameParticipant;

	// === 전투 ===
	private Integer kills;
	private Integer deaths;
	private Integer assists;
	private Integer doubleKills;
	private Integer tripleKills;
	private Integer quadraKills;
	private Integer pentaKills;
	@Column(name = "is_first_blood_kill")
	private boolean isFirstBloodKill;
	@Column(name = "is_first_blood_assist")
	private boolean isFirstBloodAssist;
	@Column(name = "is_first_blood_victim")
	private boolean isFirstBloodVictim;

	// === 데미지 ===
	private Integer damageToChampions;
	private Double dpm;
	private Double damageShare;
	private Double damageTakenPerMinute;
	private Double damageMitigatedPerMinute;

	// === 시야 ===
	private Integer wardsPlaced;
	private Double wpm;
	private Integer wardsKilled;
	private Double wcpm;
	private Integer controlWardsBought;
	private Double visionScore;
	private Double vspm;

	// === 경제 ===
	private Integer totalGold;
	private Integer earnedGold;
	private Double earnedGpm;
	private Double earnedGoldShare;
	private Integer goldSpent;

	// === CS ===
	private Integer totalCs;
	private Double cspm;
	private Integer minionKills;
	private Integer monsterKills;

	// === 시간대별 지표 ===
	// --- 10분 ---
	private Integer goldAt10;
	private Integer oppGoldAt10;
	private Integer goldDiffAt10;
	private Integer xpAt10;
	private Integer oppXpAt10;
	private Integer xpDiffAt10;
	private Integer csAt10;
	private Integer oppCsAt10;
	private Integer csdiffAt10;
	private Integer killsAt10;
	private Integer assistsAt10;
	private Integer deathsAt10;
	private Integer oppKillsAt10;
	private Integer oppAssistsAt10;
	private Integer oppDeathsAt10;

	// --- 15분 ---
	private Integer goldAt15;
	private Integer oppGoldAt15;
	private Integer goldDiffAt15;
	private Integer xpAt15;
	private Integer oppXpAt15;
	private Integer xpDiffAt15;
	private Integer csAt15;
	private Integer oppCsAt15;
	private Integer csdiffAt15;
	private Integer killsAt15;
	private Integer assistsAt15;
	private Integer deathsAt15;
	private Integer oppKillsAt15;
	private Integer oppAssistsAt15;
	private Integer oppDeathsAt15;

	// --- 20분 ---
	private Integer goldAt20;
	private Integer oppGoldAt20;
	private Integer goldDiffAt20;
	private Integer xpAt20;
	private Integer oppXpAt20;
	private Integer xpDiffAt20;
	private Integer csAt20;
	private Integer oppCsAt20;
	private Integer csdiffAt20;
	private Integer killsAt20;
	private Integer assistsAt20;
	private Integer deathsAt20;
	private Integer oppKillsAt20;
	private Integer oppAssistsAt20;
	private Integer oppDeathsAt20;

	// --- 25분 ---
	private Integer goldAt25;
	private Integer oppGoldAt25;
	private Integer goldDiffAt25;
	private Integer xpAt25;
	private Integer oppXpAt25;
	private Integer xpDiffAt25;
	private Integer csAt25;
	private Integer oppCsAt25;
	private Integer csdiffAt25;
	private Integer killsAt25;
	private Integer assistsAt25;
	private Integer deathsAt25;
	private Integer oppKillsAt25;
	private Integer oppAssistsAt25;
	private Integer oppDeathsAt25;

	public static GamePlayerStat from(GameParticipant participant, GameDataCsvDto dto) {
		return GamePlayerStat.builder()
			.gameParticipant(participant)
			// 전투
			.kills(dto.getKills())
			.deaths(dto.getDeaths())
			.assists(dto.getAssists())
			.doubleKills(dto.getDoublekills())
			.tripleKills(dto.getTriplekills())
			.quadraKills(dto.getQuadrakills())
			.pentaKills(dto.getPentakills())
			.isFirstBloodKill(toBoolean(dto.getFirstbloodkill()))
			.isFirstBloodAssist(toBoolean(dto.getFirstbloodassist()))
			.isFirstBloodVictim(toBoolean(dto.getFirstbloodvictim()))
			// 데미지
			.damageToChampions(dto.getDamagetochampions())
			.dpm(dto.getDpm())
			.damageShare(dto.getDamageshare())
			.damageTakenPerMinute(dto.getDamagetakenperminute())
			.damageMitigatedPerMinute(dto.getDamagemitigatedperminute())
			// 시야
			.wardsPlaced(dto.getWardsplaced())
			.wpm(dto.getWpm())
			.wardsKilled(dto.getWardskilled())
			.wcpm(dto.getWcpm())
			.controlWardsBought(dto.getControlwardsbought())
			.visionScore(dto.getVisionscore())
			.vspm(dto.getVspm())
			// 경제
			.totalGold(dto.getTotalgold())
			.earnedGold(dto.getEarnedgold())
			.earnedGpm(dto.getEarnedGpm())
			.earnedGoldShare(dto.getEarnedgoldshare())
			.goldSpent(dto.getGoldspent())
			// CS
			.totalCs(dto.getTotalCs())
			.cspm(dto.getCspm())
			.minionKills(dto.getMinionkills())
			.monsterKills(dto.getMonsterkills())
			// 10분 지표
			.goldAt10(dto.getGoldat10())
			.oppGoldAt10(dto.getOppGoldat10())
			.goldDiffAt10(dto.getGolddiffat10())
			.xpAt10(dto.getXpat10())
			.oppXpAt10(dto.getOppXpat10())
			.xpDiffAt10(dto.getXpdiffat10())
			.csAt10(dto.getCsat10())
			.oppCsAt10(dto.getOppCsat10())
			.csdiffAt10(dto.getCsdiffat10())
			.killsAt10(dto.getKillsat10())
			.assistsAt10(dto.getAssistsat10())
			.deathsAt10(dto.getDeathsat10())
			.oppKillsAt10(dto.getOppKillsat10())
			.oppAssistsAt10(dto.getOppAssistsat10())
			.oppDeathsAt10(dto.getOppDeathsat10())
			// 15분 지표
			.goldAt15(dto.getGoldat15())
			.oppGoldAt15(dto.getOppGoldat15())
			.goldDiffAt15(dto.getGolddiffat15())
			.xpAt15(dto.getXpat15())
			.oppXpAt15(dto.getOppXpat15())
			.xpDiffAt15(dto.getXpdiffat15())
			.csAt15(dto.getCsat15())
			.oppCsAt15(dto.getOppCsat15())
			.csdiffAt15(dto.getCsdiffat15())
			.killsAt15(dto.getKillsat15())
			.assistsAt15(dto.getAssistsat15())
			.deathsAt15(dto.getDeathsat15())
			.oppKillsAt15(dto.getOppKillsat15())
			.oppAssistsAt15(dto.getOppAssistsat15())
			.oppDeathsAt15(dto.getOppDeathsat15())
			// 20분 지표
			.goldAt20(dto.getGoldat20())
			.oppGoldAt20(dto.getOppGoldat20())
			.goldDiffAt20(dto.getGolddiffat20())
			.xpAt20(dto.getXpat20())
			.oppXpAt20(dto.getOppXpat20())
			.xpDiffAt20(dto.getXpdiffat20())
			.csAt20(dto.getCsat20())
			.oppCsAt20(dto.getOppCsat20())
			.csdiffAt20(dto.getCsdiffat20())
			.killsAt20(dto.getKillsat20())
			.assistsAt20(dto.getAssistsat20())
			.deathsAt20(dto.getDeathsat20())
			.oppKillsAt20(dto.getOppKillsat20())
			.oppAssistsAt20(dto.getOppAssistsat20())
			.oppDeathsAt20(dto.getOppDeathsat20())
			// 25분 지표
			.goldAt25(dto.getGoldat25())
			.oppGoldAt25(dto.getOppGoldat25())
			.goldDiffAt25(dto.getGolddiffat25())
			.xpAt25(dto.getXpat25())
			.oppXpAt25(dto.getOppXpat25())
			.xpDiffAt25(dto.getXpdiffat25())
			.csAt25(dto.getCsat25())
			.oppCsAt25(dto.getOppCsat25())
			.csdiffAt25(dto.getCsdiffat25())
			.killsAt25(dto.getKillsat25())
			.assistsAt25(dto.getAssistsat25())
			.deathsAt25(dto.getDeathsat25())
			.oppKillsAt25(dto.getOppKillsat25())
			.oppAssistsAt25(dto.getOppAssistsat25())
			.oppDeathsAt25(dto.getOppDeathsat25())
			.build();
	}
	private static boolean toBoolean(Integer value) {
		return Integer.valueOf(1).equals(value);
	}

}
