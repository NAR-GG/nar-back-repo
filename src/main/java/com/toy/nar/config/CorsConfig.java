package com.toy.nar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins(
				"http://localhost:3000",
				"http://localhost:8080",
				"https://nar.kr",
				"https://www.nar.kr",
				"https://api.nar.kr"
			)
			.allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
			.allowedOriginPatterns("*")
			.exposedHeaders("Authorization", "Content-Type")
			.allowCredentials(true)
			.maxAge(3600);
	}
}
