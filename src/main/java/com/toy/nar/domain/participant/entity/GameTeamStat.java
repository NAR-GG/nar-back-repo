package com.toy.nar.domain.participant.entity;

import com.toy.nar.app.data.ingestion.dto.GameDataCsvDto;
import com.toy.nar.domain.game.entity.Game;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA를 위한 기본 생성자
@AllArgsConstructor(access = AccessLevel.PRIVATE) // 빌더가 사용할 생성자
@Builder
public class GameTeamStat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "game_id", nullable = false)
	private Game game;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	// === 전투 ===
	private Integer result;
	private Integer teamKills;
	private Integer teamDeaths;
	@Column(name = "is_first_blood")
	private boolean isFirstBlood;
	private Double teamKpm;
	private Double gspd;
	private Double gpr;

	// === 드래곤 ===
	@Column(name = "is_first_dragon")
	private boolean isFirstDragon;
	private Integer dragons;
	private Integer oppDragons;
	private Integer elementaldrakes;
	private Integer oppElementaldrakes;
	private Integer infernals;
	private Integer mountains;
	private Integer clouds;
	private Integer oceans;
	private Integer chemtechs;
	private Integer hextechs;
	private Integer dragonsTypeUnknown;
	private Integer elders;
	private Integer oppElders;

	// === 전령/공허유충/바론 ===
	@Column(name = "is_first_herald")
	private boolean isFirstHerald;
	private Integer heralds;
	private Integer oppHeralds;
	private Integer voidGrubs;
	private Integer oppVoidGrubs;
	@Column(name = "is_first_baron")
	private boolean isFirstBaron;
	private Integer barons;
	private Integer oppBarons;
	private Integer atakhans;
	private Integer oppAtakhans;

	// === 타워/억제기 ===
	@Column(name = "is_first_tower")
	private boolean isFirstTower;
	private Integer towers;
	private Integer oppTowers;
	@Column(name = "is_first_mid_tower")
	private boolean isFirstMidTower;
	@Column(name = "is_first_top_tower")
	private boolean isFirstTopTower;
	@Column(name = "is_first_bot_tower")
	private boolean isFirstBotTower;
	@Column(name = "is_first_to_three_towers")
	private boolean isFirstToThreeTowers;
	private Integer turretPlates;
	private Integer oppTurretPlates;
	private Integer inhibitors;
	private Integer oppInhibitors;
	
	// === 데미지 ===
	private Integer damageToTowers;



	/**
	 * DTO로부터 GameTeamStat 엔티티를 생성하는 정적 팩토리 메소드
	 */
	public static GameTeamStat from(Game game, Team team, GameDataCsvDto dto) {
		return GameTeamStat.builder()
			.game(game)
			.team(team)
			// 전투
			.result(dto.getResult())
			.teamKills(dto.getTeamkills())
			.teamDeaths(dto.getTeamdeaths())
			.isFirstBlood(toBoolean(dto.getFirstblood()))
			.teamKpm(dto.getTeamKpm())
			.gspd(dto.getGspd())
			.gpr(dto.getGpr())
			// 드래곤
			.isFirstDragon(toBoolean(dto.getFirstdragon()))
			.dragons(dto.getDragons())
			.oppDragons(dto.getOppDragons())
			.elementaldrakes(dto.getElementaldrakes())
			.oppElementaldrakes(dto.getOppElementaldrakes())
			.infernals(dto.getInfernals())
			.mountains(dto.getMountains())
			.clouds(dto.getClouds())
			.oceans(dto.getOceans())
			.chemtechs(dto.getChemtechs())
			.hextechs(dto.getHextechs())
			.dragonsTypeUnknown(dto.getDragonsTypeUnknown())
			.elders(dto.getElders())
			.oppElders(dto.getOppElders())
			// 전령/공허유충/바론
			.isFirstHerald(toBoolean(dto.getFirstherald()))
			.heralds(dto.getHeralds())
			.oppHeralds(dto.getOppHeralds())
			.voidGrubs(dto.getVoidGrubs())
			.oppVoidGrubs(dto.getOppVoidGrubs())
			.isFirstBaron(toBoolean(dto.getFirstbaron()))
			.barons(dto.getBarons())
			.oppBarons(dto.getOppBarons())
			.atakhans(dto.getAtakhans())
			.oppAtakhans(dto.getOppAtakhans())
			// 타워/억제기
			.isFirstTower(toBoolean(dto.getFirsttower()))
			.towers(dto.getTowers())
			.oppTowers(dto.getOppTowers())
			.isFirstMidTower(toBoolean(dto.getFirstmidtower()))
			.isFirstTopTower(toBoolean(dto.getFirsttoptower()))
			.isFirstBotTower(toBoolean(dto.getFirstbottower()))
			.isFirstToThreeTowers(toBoolean(dto.getFirsttothreetowers()))
			.turretPlates(dto.getTurretplates())
			.oppTurretPlates(dto.getOppTurretplates())
			.inhibitors(dto.getInhibitors())
			.oppInhibitors(dto.getOppInhibitors())
			// 데미지
			.damageToTowers(dto.getDamagetotowers())
			.build();
	}

	private static boolean toBoolean(Integer value) {
		return Integer.valueOf(1).equals(value);
	}
}