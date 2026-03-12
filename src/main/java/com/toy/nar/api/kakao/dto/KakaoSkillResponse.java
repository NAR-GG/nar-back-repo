package com.toy.nar.api.kakao.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KakaoSkillResponse(
		String version,
		Template template
) {

	public static KakaoSkillResponse simpleText(String text, List<QuickReply> quickReplies) {
		return new KakaoSkillResponse(
				"2.0",
				new Template(
						List.of(new Output(new SimpleText(text), null)),
						quickReplies));
	}

	public static KakaoSkillResponse basicCard(String title, String description, List<Button> buttons,
			List<QuickReply> quickReplies) {
		return new KakaoSkillResponse(
				"2.0",
				new Template(
						List.of(new Output(null, new BasicCard(title, description, null, buttons))),
						quickReplies));
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Template(
			List<Output> outputs,
			List<QuickReply> quickReplies
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Output(
			SimpleText simpleText,
			BasicCard basicCard
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SimpleText(
			String text
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record BasicCard(
			String title,
			String description,
			String thumbnail,
			List<Button> buttons
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Button(
			String action,
			String label,
			String webLinkUrl,
			String messageText
	) {
		public Button(String action, String label, String webLinkUrl) {
			this(action, label, webLinkUrl, null);
		}
	}

	public record QuickReply(
			String action,
			String label,
			String messageText
	) {
	}
}
