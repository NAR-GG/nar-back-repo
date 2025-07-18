package com.toy.nar.game.dto;

import java.util.List;

public record SplitCategory(String name, Long leagueId, List<TeamSummary> teams) {}
