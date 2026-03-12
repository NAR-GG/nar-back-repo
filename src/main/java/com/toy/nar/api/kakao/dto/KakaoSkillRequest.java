package com.toy.nar.api.kakao.dto;

public record KakaoSkillRequest(
		UserRequest userRequest
) {

	public String utteranceOrEmpty() {
		return userRequest != null && userRequest.utterance() != null
				? userRequest.utterance().trim()
				: "";
	}

	public record UserRequest(
			String utterance
	) {
	}
}
