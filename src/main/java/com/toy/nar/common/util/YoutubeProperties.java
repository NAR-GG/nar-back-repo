package com.toy.nar.common.util;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.toy.nar.domain.youtube.ChannelType;

@ConfigurationProperties(prefix = "app.youtube")
public record YoutubeProperties(
	Map<ChannelType, List<String>> seedChannels
) {}
