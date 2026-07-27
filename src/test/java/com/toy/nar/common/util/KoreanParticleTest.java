package com.toy.nar.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanParticleTest {

	@Test
	void noFinalConsonantTakesRo() {
		assertThat(KoreanParticle.ro("티모")).isEqualTo("로");
		assertThat(KoreanParticle.ro("아리")).isEqualTo("로");
	}

	@Test
	void rieulFinalConsonantTakesRo() {
		assertThat(KoreanParticle.ro("멜")).isEqualTo("로");
		assertThat(KoreanParticle.ro("이즈리얼")).isEqualTo("로");
	}

	@Test
	void otherFinalConsonantTakesEuro() {
		assertThat(KoreanParticle.ro("가렌")).isEqualTo("으로");
		assertThat(KoreanParticle.ro("갱플랭크")).isEqualTo("로"); // 크: 받침 없음
		assertThat(KoreanParticle.ro("징크스")).isEqualTo("로"); // 스: 받침 없음
		assertThat(KoreanParticle.ro("문도 박사")).isEqualTo("로");
		assertThat(KoreanParticle.ro("코그모")).isEqualTo("로");
		assertThat(KoreanParticle.ro("벨코즈")).isEqualTo("로");
		assertThat(KoreanParticle.ro("자이라")).isEqualTo("로");
		assertThat(KoreanParticle.ro("판테온")).isEqualTo("으로");
		assertThat(KoreanParticle.ro("케이틀린")).isEqualTo("으로");
	}

	@Test
	void nonHangulFallsBackToRo() {
		assertThat(KoreanParticle.ro("Mel")).isEqualTo("로");
		assertThat(KoreanParticle.ro(null)).isEqualTo("로");
		assertThat(KoreanParticle.ro("")).isEqualTo("로");
	}
}
