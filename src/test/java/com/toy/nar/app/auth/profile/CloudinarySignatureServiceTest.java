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

	/**
	 * 앱은 응답의 overwrite 를 그대로 폼 필드로 실어 보낸다. Cloudinary 는
	 * file·api_key·resource_type·cloud_name·signature 를 뺀 <b>모든</b> 전송 파라미터가
	 * 서명에 포함돼야 하므로, 응답이 시키는 값과 서명 대상이 어긋나면 401 이다.
	 *
	 * <p>2026-08-29 커뮤니티 첨부가 한 장도 안 올라가던 원인이 정확히 이것이었다 —
	 * overwrite=false 를 응답에는 넣고 서명에서는 뺐다. 두 경로 모두 여기서 잠근다.
	 */
	@Test
	void 응답이_시키는_전송값과_서명_대상이_일치한다() {
		record Case(String name, ProfileImageUploadSignatureResponse res) {
		}
		for (Case c : java.util.List.of(
				new Case("프로필", service.buildProfileUpload(2L, 1700000000L)),
				new Case("커뮤니티", service.buildCommunityUpload(2L, 1700000000L)))) {

			// 앱이 실제로 보내는 서명 대상 파라미터를 그대로 재구성한다.
			Map<String, String> asClientSends = new LinkedHashMap<>();
			asClientSends.put("overwrite", String.valueOf(c.res().overwrite()));
			asClientSends.put("public_id", c.res().publicId());
			asClientSends.put("timestamp", String.valueOf(c.res().timestamp()));

			assertThat(service.sign(asClientSends))
					.as("%s: 앱 전송값으로 다시 서명하면 응답의 signature 와 같아야 한다", c.name())
					.isEqualTo(c.res().signature());
		}
	}

	@Test
	void 커뮤니티는_이미지마다_새_public_id_라_덮어쓰지_않는다() {
		ProfileImageUploadSignatureResponse first = service.buildCommunityUpload(2L, 1700000000L);
		ProfileImageUploadSignatureResponse second = service.buildCommunityUpload(2L, 1700000000L);

		assertThat(first.publicId()).startsWith("community/2/");
		assertThat(first.publicId()).isNotEqualTo(second.publicId());
		assertThat(first.overwrite()).isFalse();
	}
}
