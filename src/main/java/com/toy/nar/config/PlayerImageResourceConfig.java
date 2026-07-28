package com.toy.nar.config;

import java.nio.file.Path;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * 선수 이미지 서빙 경로. 업로드 디렉토리를 먼저 보고, 없으면 jar 안(classpath)을 본다.
 *
 * <p>순서가 중요하다: 같은 파일명으로 업로드하면 업로드분이 jar 이미지를 이긴다.
 * 덕분에 기존 65장을 일괄 이관하지 않아도 되고, 교체가 필요한 것만 자연히 옮겨간다.
 */
@Configuration
@RequiredArgsConstructor
public class PlayerImageResourceConfig implements WebMvcConfigurer {

	private final PlayerImageProperties properties;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String uploadLocation = Path.of(properties.getDir()).toAbsolutePath().toUri().toString();
		registry.addResourceHandler("/images/**")
				.addResourceLocations(uploadLocation, "classpath:/static/images/");
	}
}
