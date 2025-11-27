package com.toy.nar.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			// 1. 서버 URL 설정
			.addServersItem(new Server().url("/").description("Default Server"))

			// 2. 문서 기본 정보
			.info(new Info()
				.title("NAR API Document")
				.description("LoL Esports 데이터 분석 서비스 API 명세서")
				.version("v3.0.0"));
	}
}
