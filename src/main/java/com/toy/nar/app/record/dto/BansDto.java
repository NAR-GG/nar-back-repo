package com.toy.nar.app.record.dto;

import java.util.List;

public record BansDto(
	List<String> blue,
	List<String> red
) {}