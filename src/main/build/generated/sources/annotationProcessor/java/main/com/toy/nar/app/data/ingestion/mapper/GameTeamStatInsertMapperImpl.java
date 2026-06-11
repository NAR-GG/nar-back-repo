package com.toy.nar.app.data.ingestion.mapper;

import com.toy.nar.app.data.ingestion.dto.GameTeamStatInsertRow;
import com.toy.nar.domain.participant.entity.GameTeamStat;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-31T12:02:52+0900",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.2 (Eclipse Adoptium)"
)
@Component
public class GameTeamStatInsertMapperImpl implements GameTeamStatInsertMapper {

    @Override
    public GameTeamStatInsertRow toRow(Long gameId, Long teamId, GameTeamStat stat) {
        if ( gameId == null && teamId == null && stat == null ) {
            return null;
        }

        Boolean isFirstBlood = null;
        Boolean isFirstDragon = null;
        Boolean isFirstHerald = null;
        Boolean isFirstBaron = null;
        Boolean isFirstTower = null;
        Boolean isFirstMidTower = null;
        Boolean isFirstTopTower = null;
        Boolean isFirstBotTower = null;
        Boolean isFirstToThreeTowers = null;
        Integer result = null;
        Integer teamKills = null;
        Integer teamDeaths = null;
        Double teamKpm = null;
        Double gspd = null;
        Double gpr = null;
        Integer dragons = null;
        Integer oppDragons = null;
        Integer elementaldrakes = null;
        Integer oppElementaldrakes = null;
        Integer infernals = null;
        Integer mountains = null;
        Integer clouds = null;
        Integer oceans = null;
        Integer chemtechs = null;
        Integer hextechs = null;
        Integer dragonsTypeUnknown = null;
        Integer elders = null;
        Integer oppElders = null;
        Integer heralds = null;
        Integer oppHeralds = null;
        Integer voidGrubs = null;
        Integer oppVoidGrubs = null;
        Integer barons = null;
        Integer oppBarons = null;
        Integer atakhans = null;
        Integer oppAtakhans = null;
        Integer towers = null;
        Integer oppTowers = null;
        Integer turretPlates = null;
        Integer oppTurretPlates = null;
        Integer inhibitors = null;
        Integer oppInhibitors = null;
        Integer damageToTowers = null;
        if ( stat != null ) {
            isFirstBlood = stat.isFirstBlood();
            isFirstDragon = stat.isFirstDragon();
            isFirstHerald = stat.isFirstHerald();
            isFirstBaron = stat.isFirstBaron();
            isFirstTower = stat.isFirstTower();
            isFirstMidTower = stat.isFirstMidTower();
            isFirstTopTower = stat.isFirstTopTower();
            isFirstBotTower = stat.isFirstBotTower();
            isFirstToThreeTowers = stat.isFirstToThreeTowers();
            result = stat.getResult();
            teamKills = stat.getTeamKills();
            teamDeaths = stat.getTeamDeaths();
            teamKpm = stat.getTeamKpm();
            gspd = stat.getGspd();
            gpr = stat.getGpr();
            dragons = stat.getDragons();
            oppDragons = stat.getOppDragons();
            elementaldrakes = stat.getElementaldrakes();
            oppElementaldrakes = stat.getOppElementaldrakes();
            infernals = stat.getInfernals();
            mountains = stat.getMountains();
            clouds = stat.getClouds();
            oceans = stat.getOceans();
            chemtechs = stat.getChemtechs();
            hextechs = stat.getHextechs();
            dragonsTypeUnknown = stat.getDragonsTypeUnknown();
            elders = stat.getElders();
            oppElders = stat.getOppElders();
            heralds = stat.getHeralds();
            oppHeralds = stat.getOppHeralds();
            voidGrubs = stat.getVoidGrubs();
            oppVoidGrubs = stat.getOppVoidGrubs();
            barons = stat.getBarons();
            oppBarons = stat.getOppBarons();
            atakhans = stat.getAtakhans();
            oppAtakhans = stat.getOppAtakhans();
            towers = stat.getTowers();
            oppTowers = stat.getOppTowers();
            turretPlates = stat.getTurretPlates();
            oppTurretPlates = stat.getOppTurretPlates();
            inhibitors = stat.getInhibitors();
            oppInhibitors = stat.getOppInhibitors();
            damageToTowers = stat.getDamageToTowers();
        }
        Long gameId1 = null;
        gameId1 = gameId;
        Long teamId1 = null;
        teamId1 = teamId;

        GameTeamStatInsertRow gameTeamStatInsertRow = new GameTeamStatInsertRow( gameId1, teamId1, result, teamKills, teamDeaths, isFirstBlood, teamKpm, gspd, gpr, isFirstDragon, dragons, oppDragons, elementaldrakes, oppElementaldrakes, infernals, mountains, clouds, oceans, chemtechs, hextechs, dragonsTypeUnknown, elders, oppElders, isFirstHerald, heralds, oppHeralds, voidGrubs, oppVoidGrubs, isFirstBaron, barons, oppBarons, atakhans, oppAtakhans, isFirstTower, towers, oppTowers, isFirstMidTower, isFirstTopTower, isFirstBotTower, isFirstToThreeTowers, turretPlates, oppTurretPlates, inhibitors, oppInhibitors, damageToTowers );

        return gameTeamStatInsertRow;
    }
}
