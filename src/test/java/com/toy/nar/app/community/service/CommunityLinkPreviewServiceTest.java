package com.toy.nar.app.community.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.toy.nar.common.error.exception.CustomException;

/** SSRF 가드만 네트워크 없이 검증한다 — 실제 크롤은 prod 스모크(curl)로 확인. */
class CommunityLinkPreviewServiceTest {

	private final CommunityLinkPreviewService service = new CommunityLinkPreviewService();

	@Test
	void 사설_루프백_링크로컬_IP는_거부한다() {
		assertThatThrownBy(() -> service.preview("http://127.0.0.1/admin"))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.preview("http://10.0.0.5/"))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.preview("http://192.168.0.1/"))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.preview("http://169.254.169.254/latest/meta-data"))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.preview("http://localhost:8080/actuator"))
				.isInstanceOf(CustomException.class);
	}

	@Test
	void http_https_외_스킴과_비정상_URL은_거부한다() {
		assertThatThrownBy(() -> service.preview("file:///etc/passwd"))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.preview("ftp://example.com/x"))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.preview("")).isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.preview(null)).isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.preview("https://" + "a".repeat(600) + ".com"))
				.isInstanceOf(CustomException.class);
	}
}
