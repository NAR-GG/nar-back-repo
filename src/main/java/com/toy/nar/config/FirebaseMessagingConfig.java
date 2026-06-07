package com.toy.nar.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class FirebaseMessagingConfig {

	@Bean
	@ConditionalOnProperty(
			prefix = "firebase.messaging",
			name = "enabled",
			havingValue = "true")
	public FirebaseApp firebaseApp() throws IOException {
		FirebaseOptions options = FirebaseOptions.builder()
				.setCredentials(GoogleCredentials.getApplicationDefault())
				.build();
		return FirebaseApp.getApps().stream()
				.findFirst()
				.orElseGet(() -> FirebaseApp.initializeApp(options));
	}

	@Bean
	@ConditionalOnProperty(
			prefix = "firebase.messaging",
			name = "enabled",
			havingValue = "true")
	public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
		return FirebaseMessaging.getInstance(firebaseApp);
	}
}
