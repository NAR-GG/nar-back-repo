package com.toy.nar.app.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.auth.profile.CloudinarySignatureService;
import com.toy.nar.common.error.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class CommunityBlockValidatorTest {

	@Mock
	private CloudinarySignatureService cloudinary;

	private CommunityBlockValidator validator() {
		lenient().when(cloudinary.isOurSecureUrl(anyString()))
				.thenAnswer(inv -> ((String) inv.getArgument(0)).startsWith("https://res.cloudinary.com/ours/"));
		return new CommunityBlockValidator(new ObjectMapper(), cloudinary);
	}

	@Test
	void 텍스트_이미지_링크_임베드를_정규화하고_미리보기와_이미지를_뽑는다() {
		String body = """
				[{"type":"text","text":"  본문 첫 줄  ","style":"heading","hack":"x"},
				 {"type":"image","url":"https://res.cloudinary.com/ours/a.jpg"},
				 {"type":"link","url":"https://news.example.com/article","title":"기사"},
				 {"type":"embed","provider":"youtube","url":"https://www.youtube.com/watch?v=abc"}]
				""";

		var parsed = validator().validate(body);

		// 미리보기는 첫 text 블록 trim 앞 150자
		assertThat(parsed.preview()).isEqualTo("본문 첫 줄");
		assertThat(parsed.imageUrls()).containsExactly("https://res.cloudinary.com/ours/a.jpg");
		// 정규화: 모르는 필드(hack)는 떨어져 나간다
		assertThat(parsed.normalizedBody()).doesNotContain("hack");
		assertThat(parsed.normalizedBody()).contains("\"style\":\"heading\"");
	}

	@Test
	void 외부_이미지_URL은_거부한다() {
		String body = "[{\"type\":\"image\",\"url\":\"https://evil.com/x.jpg\"}]";
		assertThatThrownBy(() -> validator().validate(body)).isInstanceOf(CustomException.class);
	}

	@Test
	void 임베드는_제공자_도메인만_통과한다() {
		assertThatThrownBy(() -> validator().validate(
				"[{\"type\":\"embed\",\"provider\":\"youtube\",\"url\":\"https://evil.com/watch\"}]"))
				.isInstanceOf(CustomException.class);
		// 서브도메인은 통과 (clips.twitch 류 위장 도메인 youtube.com.evil.com 은 suffix 매칭에 안 걸린다)
		assertThatThrownBy(() -> validator().validate(
				"[{\"type\":\"embed\",\"provider\":\"youtube\",\"url\":\"https://youtube.com.evil.com/x\"}]"))
				.isInstanceOf(CustomException.class);
		var parsed = validator().validate(
				"[{\"type\":\"embed\",\"provider\":\"chzzk\",\"url\":\"https://chzzk.naver.com/live/abc\"}]");
		assertThat(parsed.normalizedBody()).contains("chzzk");
	}

	@Test
	void 블록수_텍스트합_이미지수_상한을_지킨다() {
		String many = "[" + "{\"type\":\"text\",\"text\":\"a\"},".repeat(50)
				+ "{\"type\":\"text\",\"text\":\"a\"}]";
		assertThatThrownBy(() -> validator().validate(many)).isInstanceOf(CustomException.class);

		String longText = "[{\"type\":\"text\",\"text\":\"" + "가".repeat(10_001) + "\"}]";
		assertThatThrownBy(() -> validator().validate(longText)).isInstanceOf(CustomException.class);

		String images = "[" + "{\"type\":\"image\",\"url\":\"https://res.cloudinary.com/ours/a.jpg\"},".repeat(5)
				+ "{\"type\":\"image\",\"url\":\"https://res.cloudinary.com/ours/a.jpg\"}]";
		assertThatThrownBy(() -> validator().validate(images)).isInstanceOf(CustomException.class);
	}

	@Test
	void 배열이_아니거나_모르는_타입이면_거부한다() {
		assertThatThrownBy(() -> validator().validate("{\"type\":\"text\"}"))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> validator().validate("[]")).isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> validator().validate("[{\"type\":\"video\",\"url\":\"https://a.com\"}]"))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> validator().validate("not json")).isInstanceOf(CustomException.class);
	}

	@Test
	void 텍스트가_없는_글은_미리보기가_null_이다() {
		var parsed = validator().validate(
				"[{\"type\":\"image\",\"url\":\"https://res.cloudinary.com/ours/a.jpg\"}]");
		assertThat(parsed.preview()).isNull();
		assertThat(parsed.imageUrls()).hasSize(1);
	}

	@Test
	void 링크는_임의_도메인이_되지만_http_스킴만_된다() {
		var parsed = validator().validate("[{\"type\":\"link\",\"url\":\"https://any-blog.io/post\"}]");
		assertThat(parsed.normalizedBody()).contains("any-blog.io");
		assertThatThrownBy(() -> validator().validate("[{\"type\":\"link\",\"url\":\"javascript:alert(1)\"}]"))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> validator().validate("[{\"type\":\"link\",\"url\":\"file:///etc/passwd\"}]"))
				.isInstanceOf(CustomException.class);
	}
}
