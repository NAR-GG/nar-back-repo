package com.toy.nar.dto;

import com.opencsv.bean.CsvBindByName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class GameDataCsvDto {

	// === 게임 기본 정보 ===
	@CsvBindByName(column = "gameid") private String gameid;
	@CsvBindByName(column = "datacompleteness") private String datacompleteness;
	@CsvBindByName(column = "url") private String url;
	@CsvBindByName(column = "league") private String league;
	@CsvBindByName(column = "year") private Integer year;
	@CsvBindByName(column = "split") private String split;
	@CsvBindByName(column = "playoffs") private Integer playoffs;
	@CsvBindByName(column = "date") private String date;
	@CsvBindByName(column = "game") private Integer game;
	@CsvBindByName(column = "patch") private String patch;
	@CsvBindByName(column = "gamelength") private Integer gamelength;

	// === 플레이어 및 팀 정보 ===
	@CsvBindByName(column = "participantid") private String participantid;
	@CsvBindByName(column = "side") private String side;
	@CsvBindByName(column = "position") private String position;
	@CsvBindByName(column = "playername") private String playername;
	@CsvBindByName(column = "playerid") private String playerid;
	@CsvBindByName(column = "teamname") private String teamname;
	@CsvBindByName(column = "teamid") private String teamid;
	@CsvBindByName(column = "champion") private String champion;

	// === 밴픽 정보 ===
	@CsvBindByName(column = "ban1") private String ban1;
	@CsvBindByName(column = "ban2") private String ban2;
	@CsvBindByName(column = "ban3") private String ban3;
	@CsvBindByName(column = "ban4") private String ban4;
	@CsvBindByName(column = "ban5") private String ban5;
	@CsvBindByName(column = "pick1") private String pick1;
	@CsvBindByName(column = "pick2") private String pick2;
	@CsvBindByName(column = "pick3") private String pick3;
	@CsvBindByName(column = "pick4") private String pick4;
	@CsvBindByName(column = "pick5") private String pick5;

	// === 기본 전투 기록 ===
	@CsvBindByName(column = "result") private Integer result;
	@CsvBindByName(column = "kills") private Integer kills;
	@CsvBindByName(column = "deaths") private Integer deaths;
	@CsvBindByName(column = "assists") private Integer assists;
	@CsvBindByName(column = "teamkills") private Integer teamkills;
	@CsvBindByName(column = "teamdeaths") private Integer teamdeaths;
	@CsvBindByName(column = "doublekills") private Integer doublekills;
	@CsvBindByName(column = "triplekills") private Integer triplekills;
	@CsvBindByName(column = "quadrakills") private Integer quadrakills;
	@CsvBindByName(column = "pentakills") private Integer pentakills;
	@CsvBindByName(column = "firstblood") private Integer firstblood;
	@CsvBindByName(column = "firstbloodkill") private Integer firstbloodkill;
	@CsvBindByName(column = "firstbloodassist") private Integer firstbloodassist;
	@CsvBindByName(column = "firstbloodvictim") private Integer firstbloodvictim;
	@CsvBindByName(column = "team kpm") private Double teamKpm;
	@CsvBindByName(column = "ckpm") private Double ckpm;

	// === 오브젝트: 드래곤 ===
	@CsvBindByName(column = "firstdragon") private Integer firstdragon;
	@CsvBindByName(column = "dragons") private Integer dragons;
	@CsvBindByName(column = "opp_dragons") private Integer oppDragons;
	@CsvBindByName(column = "elementaldrakes") private Integer elementaldrakes;
	@CsvBindByName(column = "opp_elementaldrakes") private Integer oppElementaldrakes;
	@CsvBindByName(column = "infernals") private Integer infernals;
	@CsvBindByName(column = "mountains") private Integer mountains;
	@CsvBindByName(column = "clouds") private Integer clouds;
	@CsvBindByName(column = "oceans") private Integer oceans;
	@CsvBindByName(column = "chemtechs") private Integer chemtechs;
	@CsvBindByName(column = "hextechs") private Integer hextechs;
	@CsvBindByName(column = "dragons (type unknown)") private Integer dragonsTypeUnknown;
	@CsvBindByName(column = "elders") private Integer elders;
	@CsvBindByName(column = "opp_elders") private Integer oppElders;

	// === 오브젝트: 전령 & 바론 ===
	@CsvBindByName(column = "firstherald") private Integer firstherald;
	@CsvBindByName(column = "heralds") private Integer heralds;
	@CsvBindByName(column = "opp_heralds") private Integer oppHeralds;
	@CsvBindByName(column = "void_grubs") private Integer voidGrubs;
	@CsvBindByName(column = "opp_void_grubs") private Integer oppVoidGrubs;
	@CsvBindByName(column = "firstbaron") private Integer firstbaron;
	@CsvBindByName(column = "barons") private Integer barons;
	@CsvBindByName(column = "opp_barons") private Integer oppBarons;
	@CsvBindByName(column = "atakhans") private Integer atakhans;
	@CsvBindByName(column = "opp_atakhans") private Integer oppAtakhans;

	// === 오브젝트: 타워 & 억제기 ===
	@CsvBindByName(column = "firsttower") private Integer firsttower;
	@CsvBindByName(column = "towers") private Integer towers;
	@CsvBindByName(column = "opp_towers") private Integer oppTowers;
	@CsvBindByName(column = "firstmidtower") private Integer firstmidtower;
	@CsvBindByName(column = "firsttoptower") private Integer firsttoptower;
	@CsvBindByName(column = "firstbottower") private Integer firstbottower;
	@CsvBindByName(column = "firsttothreetowers") private Integer firsttothreetowers;
	@CsvBindByName(column = "turretplates") private Integer turretplates;
	@CsvBindByName(column = "opp_turretplates") private Integer oppTurretplates;
	@CsvBindByName(column = "inhibitors") private Integer inhibitors;
	@CsvBindByName(column = "opp_inhibitors") private Integer oppInhibitors;

	// === 데미지 관련 ===
	@CsvBindByName(column = "damagetochampions") private Integer damagetochampions;
	@CsvBindByName(column = "dpm") private Double dpm;
	@CsvBindByName(column = "damageshare") private Double damageshare;
	@CsvBindByName(column = "damagetakenperminute") private Double damagetakenperminute;
	@CsvBindByName(column = "damagemitigatedperminute") private Double damagemitigatedperminute;

	// === 시야 관련 ===
	@CsvBindByName(column = "wardsplaced") private Integer wardsplaced;
	@CsvBindByName(column = "wpm") private Double wpm;
	@CsvBindByName(column = "wardskilled") private Integer wardskilled;
	@CsvBindByName(column = "wcpm") private Double wcpm;
	@CsvBindByName(column = "controlwardsbought") private Integer controlwardsbought;
	@CsvBindByName(column = "visionscore") private Double visionscore;
	@CsvBindByName(column = "vspm") private Double vspm;

	// === 골드 및 CS 관련 ===
	@CsvBindByName(column = "totalgold") private Integer totalgold;
	@CsvBindByName(column = "earnedgold") private Integer earnedgold;
	@CsvBindByName(column = "earned gpm") private Double earnedGpm;
	@CsvBindByName(column = "earnedgoldshare") private Double earnedgoldshare;
	@CsvBindByName(column = "goldspent") private Integer goldspent;
	@CsvBindByName(column = "gspd") private Double gspd;
	@CsvBindByName(column = "gpr") private Double gpr;
	@CsvBindByName(column = "total cs") private Integer totalCs;
	@CsvBindByName(column = "minionkills") private Integer minionkills;
	@CsvBindByName(column = "monsterkills") private Integer monsterkills;
	@CsvBindByName(column = "monsterkillsownjungle") private Integer monsterkillsownjungle;
	@CsvBindByName(column = "monsterkillsenemyjungle") private Integer monsterkillsenemyjungle;
	@CsvBindByName(column = "cspm") private Double cspm;

	// === 10분 지표 ===
	@CsvBindByName(column = "goldat10") private Integer goldat10;
	@CsvBindByName(column = "xpat10") private Integer xpat10;
	@CsvBindByName(column = "csat10") private Integer csat10;
	@CsvBindByName(column = "opp_goldat10") private Integer oppGoldat10;
	@CsvBindByName(column = "opp_xpat10") private Integer oppXpat10;
	@CsvBindByName(column = "opp_csat10") private Integer oppCsat10;
	@CsvBindByName(column = "golddiffat10") private Integer golddiffat10;
	@CsvBindByName(column = "xpdiffat10") private Integer xpdiffat10;
	@CsvBindByName(column = "csdiffat10") private Integer csdiffat10;
	@CsvBindByName(column = "killsat10") private Integer killsat10;
	@CsvBindByName(column = "assistsat10") private Integer assistsat10;
	@CsvBindByName(column = "deathsat10") private Integer deathsat10;
	@CsvBindByName(column = "opp_killsat10") private Integer oppKillsat10;
	@CsvBindByName(column = "opp_assistsat10") private Integer oppAssistsat10;
	@CsvBindByName(column = "opp_deathsat10") private Integer oppDeathsat10;

	// === 15분 지표 ===
	@CsvBindByName(column = "goldat15") private Integer goldat15;
	@CsvBindByName(column = "xpat15") private Integer xpat15;
	@CsvBindByName(column = "csat15") private Integer csat15;
	@CsvBindByName(column = "opp_goldat15") private Integer oppGoldat15;
	@CsvBindByName(column = "opp_xpat15") private Integer oppXpat15;
	@CsvBindByName(column = "opp_csat15") private Integer oppCsat15;
	@CsvBindByName(column = "golddiffat15") private Integer golddiffat15;
	@CsvBindByName(column = "xpdiffat15") private Integer xpdiffat15;
	@CsvBindByName(column = "csdiffat15") private Integer csdiffat15;
	@CsvBindByName(column = "killsat15") private Integer killsat15;
	@CsvBindByName(column = "assistsat15") private Integer assistsat15;
	@CsvBindByName(column = "deathsat15") private Integer deathsat15;
	@CsvBindByName(column = "opp_killsat15") private Integer oppKillsat15;
	@CsvBindByName(column = "opp_assistsat15") private Integer oppAssistsat15;
	@CsvBindByName(column = "opp_deathsat15") private Integer oppDeathsat15;

	// === 20분 지표 ===
	@CsvBindByName(column = "goldat20") private Integer goldat20;
	@CsvBindByName(column = "xpat20") private Integer xpat20;
	@CsvBindByName(column = "csat20") private Integer csat20;
	@CsvBindByName(column = "opp_goldat20") private Integer oppGoldat20;
	@CsvBindByName(column = "opp_xpat20") private Integer oppXpat20;
	@CsvBindByName(column = "opp_csat20") private Integer oppCsat20;
	@CsvBindByName(column = "golddiffat20") private Integer golddiffat20;
	@CsvBindByName(column = "xpdiffat20") private Integer xpdiffat20;
	@CsvBindByName(column = "csdiffat20") private Integer csdiffat20;
	@CsvBindByName(column = "killsat20") private Integer killsat20;
	@CsvBindByName(column = "assistsat20") private Integer assistsat20;
	@CsvBindByName(column = "deathsat20") private Integer deathsat20;
	@CsvBindByName(column = "opp_killsat20") private Integer oppKillsat20;
	@CsvBindByName(column = "opp_assistsat20") private Integer oppAssistsat20;
	@CsvBindByName(column = "opp_deathsat20") private Integer oppDeathsat20;

	// === 25분 지표 ===
	@CsvBindByName(column = "goldat25") private Integer goldat25;
	@CsvBindByName(column = "xpat25") private Integer xpat25;
	@CsvBindByName(column = "csat25") private Integer csat25;
	@CsvBindByName(column = "opp_goldat25") private Integer oppGoldat25;
	@CsvBindByName(column = "opp_xpat25") private Integer oppXpat25;
	@CsvBindByName(column = "opp_csat25") private Integer oppCsat25;
	@CsvBindByName(column = "golddiffat25") private Integer golddiffat25;
	@CsvBindByName(column = "xpdiffat25") private Integer xpdiffat25;
	@CsvBindByName(column = "csdiffat25") private Integer csdiffat25;
	@CsvBindByName(column = "killsat25") private Integer killsat25;
	@CsvBindByName(column = "assistsat25") private Integer assistsat25;
	@CsvBindByName(column = "deathsat25") private Integer deathsat25;
	@CsvBindByName(column = "opp_killsat25") private Integer oppKillsat25;
	@CsvBindByName(column = "opp_assistsat25") private Integer oppAssistsat25;
	@CsvBindByName(column = "opp_deathsat25") private Integer oppDeathsat25;
}