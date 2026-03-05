package com.toy.nar.app.category.dto;

import java.util.List;

public record SplitCategory(String name, Long leagueId, List<TeamSummary> teams, List<String> patches) {}
