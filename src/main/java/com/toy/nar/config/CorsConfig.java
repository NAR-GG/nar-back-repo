package com.toy.nar.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private static final List<String> ALLOWED_ORIGINS = List.of(
		"http://localhost:3000",
		"http://localhost:5173",   // 백오피스(Vite) 로컬 개발
		"https://d1q54t7r1dfm7m.cloudfront.net",
		"https://nar.kr",
		"https://admin.nar.kr"     // 백오피스 배포
	);

	private static final List<String> ALLOWED_METHODS = List.of(
		"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
	);

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins(ALLOWED_ORIGINS.toArray(String[]::new))
			.allowedMethods(ALLOWED_METHODS.toArray(String[]::new))
			.allowedHeaders("*")
			.allowCredentials(true);
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();

		config.setAllowCredentials(true);
		config.setAllowedOrigins(ALLOWED_ORIGINS);
		config.setAllowedHeaders(List.of("*"));
		config.setAllowedMethods(ALLOWED_METHODS);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
