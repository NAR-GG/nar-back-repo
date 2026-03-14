package com.toy.nar.app.riot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "riot.monitor")
public class RiotMonitorProperties {
	private boolean enabled;
	private long pollIntervalMs = 60000;
	private long initialDelayMs = 15000;
	private String targetLeague = "LCK";
	private String platform = "KR";
	private int recentMatchFetchCount = 5;
}
