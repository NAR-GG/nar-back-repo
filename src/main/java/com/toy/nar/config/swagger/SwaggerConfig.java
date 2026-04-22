package com.toy.nar.config.swagger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.toy.nar.common.error.ErrorResponse;

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
				.version("v3.0.0"))
			.components(new Components()
				.addSecuritySchemes("bearerAuth", new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("소셜 로그인 성공 후 발급된 Access Token을 입력합니다.")))
			.path("/oauth2/authorization/{registrationId}", new PathItem()
				.get(new Operation()
					.addTagsItem("8. 인증 / 로그인")
					.summary("소셜 로그인 시작")
					.description("소셜 로그인 인증을 시작합니다. 호출 시 OAuth 제공자 로그인 페이지로 302 리다이렉트됩니다.")
					.addParametersItem(new Parameter()
						.in("path")
						.required(true)
						.name("registrationId")
						.description("소셜 로그인 제공자")
						.schema(new StringSchema()._enum(List.of("google", "kakao", "naver"))))
					.responses(new ApiResponses()
						.addApiResponse("302", new ApiResponse().description("소셜 로그인 제공자 인증 페이지로 리다이렉트")))))
			.path("/login/oauth2/code/{registrationId}", new PathItem()
				.get(new Operation()
					.addTagsItem("8. 인증 / 로그인")
					.summary("소셜 로그인 콜백")
					.description("OAuth 제공자 인증 완료 후 호출되는 콜백 엔드포인트입니다. 일반적으로 사용자가 직접 호출하지 않습니다.")
					.addParametersItem(new Parameter()
						.in("path")
						.required(true)
						.name("registrationId")
						.description("소셜 로그인 제공자")
						.schema(new StringSchema()._enum(List.of("google", "kakao", "naver"))))
					.responses(new ApiResponses()
						.addApiResponse("302", new ApiResponse().description("프론트엔드 콜백 페이지로 리다이렉트"))
						.addApiResponse("400", new ApiResponse().description("OAuth state/code 검증 실패")))));
	}

	@Bean
	public OperationCustomizer customize() {
		return (operation, handlerMethod) -> {
			ApiErrorCode apiErrorCode = handlerMethod.getMethodAnnotation(ApiErrorCode.class);

			// 어노테이션이 없으면 패스
			if (apiErrorCode == null) {
				return operation;
			}

			// ErrorCode들을 HTTP Status 별로 그룹화 (예: 400끼리, 404끼리)
			Map<Integer, List<ExampleHolder>> statusWithExampleHolders = Arrays.stream(apiErrorCode.value())
				.map(errorCode -> {
					// ErrorResponse 객체 생성
					// (ErrorResponse.toResponseEntity() 로직을 참고하여 객체만 생성)
					ErrorResponse errorResponse = ErrorResponse.builder()
						.status(errorCode.getHttpStatus().value())
						.error(errorCode.getHttpStatus().name())
						.code(errorCode.name())
						.message(errorCode.getMessage())
						.build();

					return ExampleHolder.builder()
						.holder(getSwaggerExample(errorCode.name(), errorResponse))
						.code(errorCode.getHttpStatus().value())
						.name(errorCode.name())
						.build();
				})
				.collect(Collectors.groupingBy(ExampleHolder::getCode));

			// Swagger Operation에 응답 추가
			addExamplesToResponses(operation.getResponses(), statusWithExampleHolders);

			return operation;
		};
	}

	// Swagger용 Example 객체 생성 헬퍼
	private Example getSwaggerExample(String value, ErrorResponse errorResponse) {
		Example example = new Example();
		example.setValue(errorResponse);
		return example;
	}

	// 응답에 예시 추가하는 로직
	private void addExamplesToResponses(ApiResponses responses, Map<Integer, List<ExampleHolder>> statusWithExampleHolders) {
		statusWithExampleHolders.forEach((status, v) -> {
			Content content = new Content();
			MediaType mediaType = new MediaType();
			ApiResponse apiResponse = new ApiResponse();

			v.forEach(exampleHolder -> {
				mediaType.addExamples(exampleHolder.getName(), exampleHolder.getHolder());
			});

			content.addMediaType("application/json", mediaType);
			apiResponse.setContent(content);
			responses.addApiResponse(String.valueOf(status), apiResponse);
		});
	}

	// 내부용 DTO
	@lombok.Builder
	@lombok.Getter
	private static class ExampleHolder {
		private Example holder;
		private String name;
		private int code;
	}
}
