package com.toy.nar.app.youtube.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PubSubEntry {

	@JacksonXmlProperty(localName = "videoId", namespace = "http://www.youtube.com/xml/schemas/2015")
	private String videoId;

	@JacksonXmlProperty(localName = "channelId", namespace = "http://www.youtube.com/xml/schemas/2015")
	private String channelId;

	@JacksonXmlProperty(localName = "title", namespace = "http://www.w3.org/2005/Atom")
	private String title;

	@JacksonXmlProperty(localName = "published", namespace = "http://www.w3.org/2005/Atom")
	private String published;

	// 알림이 삭제된 비디오일 경우 null 체크 등이 필요할 수 있음
	public boolean isVideoNotification() {
		return videoId != null && !videoId.isBlank();
	}
}
