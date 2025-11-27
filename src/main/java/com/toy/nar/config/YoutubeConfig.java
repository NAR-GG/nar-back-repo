package com.toy.nar.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.toy.nar.common.util.YoutubeApiProperties;

@Configuration
@EnableConfigurationProperties(YoutubeApiProperties.class)
public class YoutubeConfig {

	@Bean
	public WebClient youtubeWebClient(YoutubeApiProperties props, WebClient.Builder builder) {
		return builder
			.baseUrl(props.getBaseUrl())
			.build();
	}
}
