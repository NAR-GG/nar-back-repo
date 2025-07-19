package com.toy.nar.app.category;

import java.util.List;

public record SplitCategory(String name, Long leagueId, List<TeamSummary> teams) {}
