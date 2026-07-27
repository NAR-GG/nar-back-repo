package com.toy.nar.app.riot.dto;

import jakarta.validation.constraints.NotBlank;

public record PlayerRiotAlertCheckRequest(
		@NotBlank(message = "puuid must not be blank")
		String puuid,
		// 플랫폼 라우팅 값(KR/EUW1/NA1...) 또는 지역 태그(KR/EUW/NA). 비우면 KR.
		String platform) {
}
