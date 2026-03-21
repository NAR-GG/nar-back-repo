package com.toy.nar.app.youtube;

import java.util.List;

import com.toy.nar.app.youtube.dto.YoutubeVideoResponse;

record ChannelVideoSyncPayload(
		String channelYoutubeId,
		List<YoutubeVideoResponse.VideoItem> items) {

	static ChannelVideoSyncPayload empty(String channelYoutubeId) {
		return new ChannelVideoSyncPayload(channelYoutubeId, List.of());
	}
}
