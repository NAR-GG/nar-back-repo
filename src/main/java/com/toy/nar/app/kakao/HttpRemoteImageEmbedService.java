package com.toy.nar.app.kakao;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class HttpRemoteImageEmbedService implements RemoteImageEmbedService {

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(3))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	@Override
	public Optional<EmbeddedImage> resolve(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			return Optional.empty();
		}

		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
					.timeout(Duration.ofSeconds(5))
					.GET()
					.build();
			HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null
					|| response.body().length == 0) {
				return Optional.empty();
			}

			String contentType = response.headers()
					.firstValue("Content-Type")
					.map(value -> value.split(";")[0].trim())
					.filter(value -> !value.isBlank())
					.orElse("image/png");

			String base64 = Base64.getEncoder().encodeToString(response.body());
			return Optional.of(new EmbeddedImage("data:%s;base64,%s".formatted(contentType, base64), contentType));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
