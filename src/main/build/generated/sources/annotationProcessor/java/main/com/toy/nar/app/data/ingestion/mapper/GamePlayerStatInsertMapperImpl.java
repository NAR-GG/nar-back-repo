package com.toy.nar.app.data.ingestion.mapper;

import com.toy.nar.app.data.ingestion.dto.GamePlayerStatInsertRow;
import com.toy.nar.domain.participant.entity.GamePlayerStat;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-31T12:02:52+0900",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.2 (Eclipse Adoptium)"
)
@Component
public class GamePlayerStatInsertMapperImpl implements GamePlayerStatInsertMapper {

    @Override
    public GamePlayerStatInsertRow toRow(Long gameParticipantId, GamePlayerStat stat) {
        if ( gameParticipantId == null && stat == null ) {
            return null;
        }

        Boolean isFirstBloodKill = null;
        Boolean isFirstBloodAssist = null;
        Boolean isFirstBloodVictim = null;
        Integer kills = null;
        Integer deaths = null;
        Integer assists = null;
        Integer doubleKills = null;
        Integer tripleKills = null;
        Integer quadraKills = null;
        Integer pentaKills = null;
        Integer damageToChampions = null;
        Integer damageToTowers = null;
        Double dpm = null;
        Double damageShare = null;
        Double damageTakenPerMinute = null;
        Double damageMitigatedPerMinute = null;
        Integer wardsPlaced = null;
        Double wpm = null;
        Integer wardsKilled = null;
        Double wcpm = null;
        Integer controlWardsBought = null;
        Double visionScore = null;
        Double vspm = null;
        Integer totalGold = null;
        Integer earnedGold = null;
        Double earnedGpm = null;
        Double earnedGoldShare = null;
        Integer goldSpent = null;
        Integer totalCs = null;
        Double cspm = null;
        Integer minionKills = null;
        Integer monsterKills = null;
        Integer goldAt10 = null;
        Integer oppGoldAt10 = null;
        Integer goldDiffAt10 = null;
        Integer xpAt10 = null;
        Integer oppXpAt10 = null;
        Integer xpDiffAt10 = null;
        Integer csAt10 = null;
        Integer oppCsAt10 = null;
        Integer csdiffAt10 = null;
        Integer killsAt10 = null;
        Integer assistsAt10 = null;
        Integer deathsAt10 = null;
        Integer oppKillsAt10 = null;
        Integer oppAssistsAt10 = null;
        Integer oppDeathsAt10 = null;
        Integer goldAt15 = null;
        Integer oppGoldAt15 = null;
        Integer goldDiffAt15 = null;
        Integer xpAt15 = null;
        Integer oppXpAt15 = null;
        Integer xpDiffAt15 = null;
        Integer csAt15 = null;
        Integer oppCsAt15 = null;
        Integer csdiffAt15 = null;
        Integer killsAt15 = null;
        Integer assistsAt15 = null;
        Integer deathsAt15 = null;
        Integer oppKillsAt15 = null;
        Integer oppAssistsAt15 = null;
        Integer oppDeathsAt15 = null;
        Integer goldAt20 = null;
        Integer oppGoldAt20 = null;
        Integer goldDiffAt20 = null;
        Integer xpAt20 = null;
        Integer oppXpAt20 = null;
        Integer xpDiffAt20 = null;
        Integer csAt20 = null;
        Integer oppCsAt20 = null;
        Integer csdiffAt20 = null;
        Integer killsAt20 = null;
        Integer assistsAt20 = null;
        Integer deathsAt20 = null;
        Integer oppKillsAt20 = null;
        Integer oppAssistsAt20 = null;
        Integer oppDeathsAt20 = null;
        Integer goldAt25 = null;
        Integer oppGoldAt25 = null;
        Integer goldDiffAt25 = null;
        Integer xpAt25 = null;
        Integer oppXpAt25 = null;
        Integer xpDiffAt25 = null;
        Integer csAt25 = null;
        Integer oppCsAt25 = null;
        Integer csdiffAt25 = null;
        Integer killsAt25 = null;
        Integer assistsAt25 = null;
        Integer deathsAt25 = null;
        Integer oppKillsAt25 = null;
        Integer oppAssistsAt25 = null;
        Integer oppDeathsAt25 = null;
        if ( stat != null ) {
            isFirstBloodKill = stat.isFirstBloodKill();
            isFirstBloodAssist = stat.isFirstBloodAssist();
            isFirstBloodVictim = stat.isFirstBloodVictim();
            kills = stat.getKills();
            deaths = stat.getDeaths();
            assists = stat.getAssists();
            doubleKills = stat.getDoubleKills();
            tripleKills = stat.getTripleKills();
            quadraKills = stat.getQuadraKills();
            pentaKills = stat.getPentaKills();
            damageToChampions = stat.getDamageToChampions();
            damageToTowers = stat.getDamageToTowers();
            dpm = stat.getDpm();
            damageShare = stat.getDamageShare();
            damageTakenPerMinute = stat.getDamageTakenPerMinute();
            damageMitigatedPerMinute = stat.getDamageMitigatedPerMinute();
            wardsPlaced = stat.getWardsPlaced();
            wpm = stat.getWpm();
            wardsKilled = stat.getWardsKilled();
            wcpm = stat.getWcpm();
            controlWardsBought = stat.getControlWardsBought();
            visionScore = stat.getVisionScore();
            vspm = stat.getVspm();
            totalGold = stat.getTotalGold();
            earnedGold = stat.getEarnedGold();
            earnedGpm = stat.getEarnedGpm();
            earnedGoldShare = stat.getEarnedGoldShare();
            goldSpent = stat.getGoldSpent();
            totalCs = stat.getTotalCs();
            cspm = stat.getCspm();
            minionKills = stat.getMinionKills();
            monsterKills = stat.getMonsterKills();
            goldAt10 = stat.getGoldAt10();
            oppGoldAt10 = stat.getOppGoldAt10();
            goldDiffAt10 = stat.getGoldDiffAt10();
            xpAt10 = stat.getXpAt10();
            oppXpAt10 = stat.getOppXpAt10();
            xpDiffAt10 = stat.getXpDiffAt10();
            csAt10 = stat.getCsAt10();
            oppCsAt10 = stat.getOppCsAt10();
            csdiffAt10 = stat.getCsdiffAt10();
            killsAt10 = stat.getKillsAt10();
            assistsAt10 = stat.getAssistsAt10();
            deathsAt10 = stat.getDeathsAt10();
            oppKillsAt10 = stat.getOppKillsAt10();
            oppAssistsAt10 = stat.getOppAssistsAt10();
            oppDeathsAt10 = stat.getOppDeathsAt10();
            goldAt15 = stat.getGoldAt15();
            oppGoldAt15 = stat.getOppGoldAt15();
            goldDiffAt15 = stat.getGoldDiffAt15();
            xpAt15 = stat.getXpAt15();
            oppXpAt15 = stat.getOppXpAt15();
            xpDiffAt15 = stat.getXpDiffAt15();
            csAt15 = stat.getCsAt15();
            oppCsAt15 = stat.getOppCsAt15();
            csdiffAt15 = stat.getCsdiffAt15();
            killsAt15 = stat.getKillsAt15();
            assistsAt15 = stat.getAssistsAt15();
            deathsAt15 = stat.getDeathsAt15();
            oppKillsAt15 = stat.getOppKillsAt15();
            oppAssistsAt15 = stat.getOppAssistsAt15();
            oppDeathsAt15 = stat.getOppDeathsAt15();
            goldAt20 = stat.getGoldAt20();
            oppGoldAt20 = stat.getOppGoldAt20();
            goldDiffAt20 = stat.getGoldDiffAt20();
            xpAt20 = stat.getXpAt20();
            oppXpAt20 = stat.getOppXpAt20();
            xpDiffAt20 = stat.getXpDiffAt20();
            csAt20 = stat.getCsAt20();
            oppCsAt20 = stat.getOppCsAt20();
            csdiffAt20 = stat.getCsdiffAt20();
            killsAt20 = stat.getKillsAt20();
            assistsAt20 = stat.getAssistsAt20();
            deathsAt20 = stat.getDeathsAt20();
            oppKillsAt20 = stat.getOppKillsAt20();
            oppAssistsAt20 = stat.getOppAssistsAt20();
            oppDeathsAt20 = stat.getOppDeathsAt20();
            goldAt25 = stat.getGoldAt25();
            oppGoldAt25 = stat.getOppGoldAt25();
            goldDiffAt25 = stat.getGoldDiffAt25();
            xpAt25 = stat.getXpAt25();
            oppXpAt25 = stat.getOppXpAt25();
            xpDiffAt25 = stat.getXpDiffAt25();
            csAt25 = stat.getCsAt25();
            oppCsAt25 = stat.getOppCsAt25();
            csdiffAt25 = stat.getCsdiffAt25();
            killsAt25 = stat.getKillsAt25();
            assistsAt25 = stat.getAssistsAt25();
            deathsAt25 = stat.getDeathsAt25();
            oppKillsAt25 = stat.getOppKillsAt25();
            oppAssistsAt25 = stat.getOppAssistsAt25();
            oppDeathsAt25 = stat.getOppDeathsAt25();
        }
        Long gameParticipantId1 = null;
        gameParticipantId1 = gameParticipantId;

        GamePlayerStatInsertRow gamePlayerStatInsertRow = new GamePlayerStatInsertRow( gameParticipantId1, kills, deaths, assists, doubleKills, tripleKills, quadraKills, pentaKills, isFirstBloodKill, isFirstBloodAssist, isFirstBloodVictim, damageToChampions, damageToTowers, dpm, damageShare, damageTakenPerMinute, damageMitigatedPerMinute, wardsPlaced, wpm, wardsKilled, wcpm, controlWardsBought, visionScore, vspm, totalGold, earnedGold, earnedGpm, earnedGoldShare, goldSpent, totalCs, cspm, minionKills, monsterKills, goldAt10, oppGoldAt10, goldDiffAt10, xpAt10, oppXpAt10, xpDiffAt10, csAt10, oppCsAt10, csdiffAt10, killsAt10, assistsAt10, deathsAt10, oppKillsAt10, oppAssistsAt10, oppDeathsAt10, goldAt15, oppGoldAt15, goldDiffAt15, xpAt15, oppXpAt15, xpDiffAt15, csAt15, oppCsAt15, csdiffAt15, killsAt15, assistsAt15, deathsAt15, oppKillsAt15, oppAssistsAt15, oppDeathsAt15, goldAt20, oppGoldAt20, goldDiffAt20, xpAt20, oppXpAt20, xpDiffAt20, csAt20, oppCsAt20, csdiffAt20, killsAt20, assistsAt20, deathsAt20, oppKillsAt20, oppAssistsAt20, oppDeathsAt20, goldAt25, oppGoldAt25, goldDiffAt25, xpAt25, oppXpAt25, xpDiffAt25, csAt25, oppCsAt25, csdiffAt25, killsAt25, assistsAt25, deathsAt25, oppKillsAt25, oppAssistsAt25, oppDeathsAt25 );

        return gamePlayerStatInsertRow;
    }
}
