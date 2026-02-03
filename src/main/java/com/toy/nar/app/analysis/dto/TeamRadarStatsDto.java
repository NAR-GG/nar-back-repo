package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 팀 레이더 차트 지표 DTO (21개 지표)
 * 
 * 2026 시즌 기준: 아타칸 미적용
 */
@Getter
@Builder
public class TeamRadarStatsDto {

    // === 기본 정보 ===
    private Long teamId;
    private String teamName;
    private Integer gamesPlayed;
    private Integer year;

    // === 승률 ===
    /** WIN% - 승률 (0.0 ~ 1.0) */
    private Double winRate;

    // === 시간대별 골드차 (팀 합산 평균) ===
    /** 10@GD - 10분 골드차 평균 */
    private Double goldDiffAt10;
    /** 15@GD - 15분 골드차 평균 */
    private Double goldDiffAt15;
    /** 20@GD - 20분 골드차 평균 */
    private Double goldDiffAt20;
    /** 25@GD - 25분 골드차 평균 */
    private Double goldDiffAt25;
    /** GSPD - Gold Slope Percent Difference (시간당 골드 기울기 차이) */
    private Double gspd;

    // === 전투 ===
    /** CKPM - 분당 합산 킬 (Combined Kills Per Minute) */
    private Double ckpm;

    // === 선취 지표 (First 비율) ===
    /** FB% - 퍼스트 블러드 비율 */
    private Double firstBloodRate;
    /** FT% - 퍼스트 타워 비율 */
    private Double firstTowerRate;
    /** F3T% - 첫 3타워 선취 비율 */
    private Double firstThreeTowerRate;
    /** HER% - 퍼스트 전령 비율 */
    private Double firstHeraldRate;
    /** DRG% - 퍼스트 드래곤 비율 */
    private Double firstDragonRate;
    /** BN% / FBN% - 퍼스트 바론 비율 */
    private Double firstBaronRate;

    // === 오브젝트 ===
    /** DRAPG - 경기당 드래곤 수 (Dragons Per Game) */
    private Double dragonsPerGame;
    /** GRB% - 공허유충 획득률 */
    private Double voidGrubsRate;

    // === 타워 ===
    /** TOWERS KILLED - 경기당 파괴한 타워 수 */
    private Double towersKilledAvg;
    /** TOWERS LOST - 경기당 잃은 타워 수 */
    private Double towersLostAvg;

    // === 계산 레이팅 ===
    /** EGR - Early Game Rating (초반 지배력) */
    private Double earlyGameRating;
    /** MLR - Mid-Late Rating (중후반 지배력) */
    private Double midLateRating;
    /** PPG - Points Per Game (경기당 포인트) */
    private Double pointsPerGame;
}
