package com.toy.nar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.toy.nar.common.util.YoutubeApiProperties;

@Configuration
@EnableConfigurationProperties(YoutubeApiProperties.class)
public class YoutubeConfig {

	/**
	 * 부트 기본 빌더는 응답 버퍼가 256KB 다. 설명이 긴 영상 50개를 한 번에 받는 /videos 응답이
	 * 그걸 넘어 LCK 채널 동기화가 DataBufferLimitException 으로 매일 실패했다. 공용 WebClient 와 같은 한도를 쓴다.
	 */
	@Bean
	public WebClient youtubeWebClient(YoutubeApiProperties props, WebClient.Builder builder,
			@Value("${app.web-client.max-in-memory-size-bytes:4194304}") int maxInMemorySizeBytes) {
		return builder
			.baseUrl(props.getBaseUrl())
			.codecs(c -> c.defaultCodecs().maxInMemorySize(maxInMemorySizeBytes))
			.build();
	}
}
