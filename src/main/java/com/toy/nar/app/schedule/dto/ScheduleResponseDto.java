package com.toy.nar.app.schedule.dto;

import java.util.List;

public record ScheduleResponseDto(
	String date,
	List<MatchSummaryDto> matches
) {}