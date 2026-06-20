package com.toy.nar.app.auth.profile;

import com.toy.nar.app.auth.profile.dto.ProfileImageUploadSignatureResponse;
import com.toy.nar.config.CloudinaryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinarySignatureServiceTest {

	private CloudinarySignatureService service;

	@BeforeEach
	void setUp() {
		CloudinaryProperties props = new CloudinaryProperties();
		props.setCloudName("dvvurdffw");
		props.setApiKey("KEY123");
		props.setApiSecret("testsecret123");
		service = new CloudinarySignatureService(props);
	}

	@Test
	void computesSignatureMatchingCloudinaryAlgorithm() {
		// 공식 Cloudinary Python SDK(api_sign_request)가 동일 입력으로 만든 기준값
		Map<String, String> params = new LinkedHashMap<>();
		params.put("overwrite", "true");
		params.put("public_id", "profiles/2");
		params.put("timestamp", "1700000000");

		assertThat(service.sign(params)).isEqualTo("056d67fda188951c5a19f6a9e962e3e121c290e0");
	}

	@Test
	void sortsParamsAlphabeticallyRegardlessOfInsertionOrder() {
		Map<String, String> shuffled = new LinkedHashMap<>();
		shuffled.put("timestamp", "1700000000");
		shuffled.put("public_id", "profiles/2");
		shuffled.put("overwrite", "true");

		assertThat(service.sign(shuffled)).isEqualTo("056d67fda188951c5a19f6a9e962e3e121c290e0");
	}

	@Test
	void buildsProfileUploadSignatureForMember() {
		ProfileImageUploadSignatureResponse res = service.buildProfileUpload(2L, 1700000000L);

		assertThat(res.cloudName()).isEqualTo("dvvurdffw");
		assertThat(res.apiKey()).isEqualTo("KEY123");
		assertThat(res.publicId()).isEqualTo("profiles/2");
		assertThat(res.overwrite()).isTrue();
		assertThat(res.timestamp()).isEqualTo(1700000000L);
		// 서명은 overwrite/public_id/timestamp 조합으로 계산된 위 기준값과 동일해야 한다
		assertThat(res.signature()).isEqualTo("056d67fda188951c5a19f6a9e962e3e121c290e0");
	}
}
