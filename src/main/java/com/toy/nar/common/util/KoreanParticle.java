package com.toy.nar.common.util;

/** 한국어 조사 선택 유틸. */
public final class KoreanParticle {

	private KoreanParticle() {
	}

	/**
	 * "로/으로" 선택. 받침 없음(티모 → 티모로)·ㄹ받침(멜 → 멜로) → "로",
	 * 그 외 받침(가렌 → 가렌으로) → "으로". 한글이 아닌 마지막 글자(영문·숫자 등)는 "로" 폴백.
	 */
	public static String ro(String word) {
		if (word == null || word.isBlank()) {
			return "로";
		}
		char last = word.charAt(word.length() - 1);
		if (last < 0xAC00 || last > 0xD7A3) {
			return "로";
		}
		int jongseong = (last - 0xAC00) % 28;
		return (jongseong == 0 || jongseong == 8) ? "로" : "으로";
	}
}
