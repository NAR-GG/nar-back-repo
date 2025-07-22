package com.toy.nar.app.category.dto;

import java.util.List;

public record SeasonCategory(Integer year, List<LeagueCategory> leagues) {}
