package com.toy.nar.app.lolesports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

public class LolesportsResponse {

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Root {
		private DataObj data;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DataObj {
		private Schedule schedule;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Schedule {
		private List<Event> events;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Event {
		private String startTime;
		private String state;
		private Match match;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Match {
		private String id;
		private List<Team> teams;

		@JsonProperty("games") // 명시적 매핑
		private List<Game> games;

		private Strategy strategy;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Team {
		private String name;
		private String code;
		private Result result;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Result {
		private int gameWins;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Strategy {
		private int count;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Game {
		private String id;
		private String state;

		@JsonProperty("vods") // 명시적 매핑
		private List<Vod> vods;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Vod {
		private String parameter;
		private String locale;
		private String provider;
		private Long startMillis;
	}
}