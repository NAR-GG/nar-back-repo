package com.toy.nar.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;


@Configuration
public class CorsConfig {

	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilter() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();

		// 1. 자격 증명 허용 (쿠키, 인증 헤더 등)
		config.setAllowCredentials(true);

		// 2. 모든 도메인 허용 (패턴 사용)
		config.setAllowedOriginPatterns(List.of("*"));

		// 3. 모든 헤더 허용
		config.setAllowedHeaders(List.of("*"));

		// 4. 모든 메서드 허용
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

		source.registerCorsConfiguration("/**", config);

		// 5. 필터 등록 및 우선순위 최상위 설정
		FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return bean;
	}
}