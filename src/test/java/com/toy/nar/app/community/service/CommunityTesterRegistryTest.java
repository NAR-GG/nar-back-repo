package com.toy.nar.app.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 테스터 목록 파싱 — 잘못된 설정이 "모두 테스터"가 되면 테스트 글이 새어 나간다. */
class CommunityTesterRegistryTest {

	@Test
	void 비어있으면_아무도_테스터가_아니다() {
		assertThat(new CommunityTesterRegistry("").isTester(12L)).isFalse();
		assertThat(new CommunityTesterRegistry(null).isTester(12L)).isFalse();
		assertThat(new CommunityTesterRegistry("  ").isTester(12L)).isFalse();
	}

	@Test
	void 쉼표목록과_공백을_파싱한다() {
		CommunityTesterRegistry registry = new CommunityTesterRegistry(" 12 , 34 ");
		assertThat(registry.isTester(12L)).isTrue();
		assertThat(registry.isTester(34L)).isTrue();
		assertThat(registry.isTester(99L)).isFalse();
		assertThat(registry.isTester(null)).isFalse();
	}
}
