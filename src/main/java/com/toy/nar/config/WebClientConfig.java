package com.toy.nar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

	@Value("${app.web-client.max-in-memory-size-bytes:4194304}")
	private int maxInMemorySizeBytes;

	@Bean
	public WebClient webClient() {
		return WebClient.builder()
				.codecs(this::configureCodecs)
				.build();
	}

	private void configureCodecs(ClientCodecConfigurer configurer) {
		configurer.defaultCodecs().maxInMemorySize(maxInMemorySizeBytes);
	}
}
