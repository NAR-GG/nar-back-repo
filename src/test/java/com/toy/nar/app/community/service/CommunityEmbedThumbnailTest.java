package com.toy.nar.app.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.toy.nar.domain.community.repository.CommunityPostRow;

/** 목록 썸네일 파생 — 첨부 사진이 없는 임베드·링크 글도 목록에서 비어 보이지 않게. */
class CommunityEmbedThumbnailTest {

	private static CommunityPostRow row(String bodyFormat, String body) {
		return new CommunityPostRow(1L, null, null, "제목", body, bodyFormat, null,
				0, 0, 0, "VISIBLE", LocalDateTime.now(), null,
				7L, "닉", "0001", null, null, null, null, false, null);
	}

	@Test
	void 유튜브_임베드는_영상_썸네일을_쓴다() {
		String body = "[{\"type\":\"embed\",\"provider\":\"youtube\","
				+ "\"url\":\"https://youtu.be/ddB-Bpzb0Ho?si=37ez\"}]";
		assertThat(CommunityPostService.embedThumbnail(row("BLOCKS", body)))
				.isEqualTo("https://img.youtube.com/vi/ddB-Bpzb0Ho/hqdefault.jpg");

		String watch = "[{\"type\":\"embed\",\"provider\":\"youtube\","
				+ "\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1\"}]";
		assertThat(CommunityPostService.embedThumbnail(row("BLOCKS", watch)))
				.contains("dQw4w9WgXcQ");

		String shorts = "[{\"type\":\"embed\",\"provider\":\"youtube\","
				+ "\"url\":\"https://youtube.com/shorts/abc123\"}]";
		assertThat(CommunityPostService.embedThumbnail(row("BLOCKS", shorts)))
				.contains("abc123");
	}

	@Test
	void 링크_카드는_OG_이미지를_쓴다() {
		String body = "[{\"type\":\"text\",\"text\":\"보세요\"},"
				+ "{\"type\":\"link\",\"url\":\"https://a.com\",\"imageUrl\":\"https://a.com/og.png\"}]";
		assertThat(CommunityPostService.embedThumbnail(row("BLOCKS", body)))
				.isEqualTo("https://a.com/og.png");
	}

	@Test
	void 후보가_없거나_평문이면_null() {
		assertThat(CommunityPostService.embedThumbnail(
				row("BLOCKS", "[{\"type\":\"text\",\"text\":\"글만\"}]"))).isNull();
		// 치지직 등 썸네일을 모르는 제공자는 비운다(잘못된 이미지보다 없는 게 낫다)
		assertThat(CommunityPostService.embedThumbnail(row("BLOCKS",
				"[{\"type\":\"embed\",\"provider\":\"chzzk\",\"url\":\"https://chzzk.naver.com/x\"}]")))
				.isNull();
		assertThat(CommunityPostService.embedThumbnail(row("PLAIN", "평문 본문"))).isNull();
		// 깨진 JSON 이어도 목록이 깨지지 않는다
		assertThat(CommunityPostService.embedThumbnail(row("BLOCKS", "not json"))).isNull();
	}
}
