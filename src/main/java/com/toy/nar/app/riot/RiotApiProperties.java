package com.toy.nar.app.riot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "riot.api")
public class RiotApiProperties {
	private boolean enabled;
	private String key;
	private String regionalBaseUrl = "https://asia.api.riotgames.com";
	private String krBaseUrl = "https://kr.api.riotgames.com";
	private long requestTimeoutMs = 3000;
}
