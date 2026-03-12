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
						List.of(new Output(new SimpleText(text), null, null, null)),
						quickReplies));
	}

	public static KakaoSkillResponse basicCard(String title, String description, List<Button> buttons,
			List<QuickReply> quickReplies) {
		return new KakaoSkillResponse(
				"2.0",
				new Template(
						List.of(new Output(null, null, new BasicCard(title, description, null, buttons), null)),
						quickReplies));
	}

	public static KakaoSkillResponse textCard(String title, String description, List<Button> buttons,
			List<QuickReply> quickReplies) {
		return new KakaoSkillResponse(
				"2.0",
				new Template(
						List.of(new Output(null, new TextCard(title, description, buttons, null), null, null)),
						quickReplies));
	}

	public static KakaoSkillResponse listCard(String title, List<ListItem> items, List<Button> buttons,
			List<QuickReply> quickReplies) {
		return new KakaoSkillResponse(
				"2.0",
				new Template(
						List.of(new Output(null, null, null, new ListCard(new ListItem(title, null, null, null), items, buttons, null))),
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
			TextCard textCard,
			BasicCard basicCard,
			ListCard listCard
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
	public record TextCard(
			String title,
			String description,
			List<Button> buttons,
			String buttonLayout
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ListCard(
			ListItem header,
			List<ListItem> items,
			List<Button> buttons,
			String buttonLayout
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ListItem(
			String title,
			String description,
			String imageUrl,
			Link link
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Link(
			String web,
			String mobile,
			String pc
	) {
		public Link(String web) {
			this(web, web, web);
		}
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
