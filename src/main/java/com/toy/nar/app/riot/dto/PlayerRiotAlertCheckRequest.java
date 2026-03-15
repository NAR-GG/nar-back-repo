package com.toy.nar.app.riot.dto;

import jakarta.validation.constraints.NotBlank;

public record PlayerRiotAlertCheckRequest(
		@NotBlank(message = "puuid must not be blank")
		String puuid) {
}
