package com.toy.nar.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cloudinary")
public class CloudinaryProperties {
	private String cloudName;
	private String apiKey;
	private String apiSecret;

	/**
	 * Riot 계열 원본 이미지를 Cloudinary fetch 로 감쌀지. 끄면 원본 URL 을 그대로 저장·전송한다
	 * (쿼터 소진·장애 시 킬 스위치). 되돌릴 때는 이 값을 false 로 내린 뒤 언랩 SQL 을 돌린다 —
	 * 원본 URL 이 fetch URL 안에 그대로 남아 있어 복원 가능하다.
	 */
	private boolean cdnEnabled = true;
}
