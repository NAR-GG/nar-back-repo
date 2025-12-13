package com.toy.nar.common.util;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "google.youtube.api")
public class YoutubeApiProperties {
	private String key;
	private String baseUrl;
	private String pubSubHubbubUrl = "https://pubsubhubbub.appspot.com";
}
