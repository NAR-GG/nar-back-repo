package com.toy.nar.app.kakao;

import java.util.Optional;

public interface RemoteImageEmbedService {

	Optional<EmbeddedImage> resolve(String imageUrl);

	record EmbeddedImage(
			String dataUri,
			String contentType
	) {
	}
}
