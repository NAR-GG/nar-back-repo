package com.toy.nar.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseMessagingConfigRebuildTest {

	@AfterEach
	void tearDown() {
		FirebaseApp.getApps().forEach(FirebaseApp::delete);
	}

	@Test
	@DisplayName("재생성하면 같은 자격증명·새 transport 의 새 앱이 만들어지고 이어서 또 재생성할 수 있다")
	void 재생성하면_새_앱으로_바뀐다() {
		FirebaseApp original = FirebaseApp.initializeApp(FirebaseOptions.builder()
				.setCredentials(GoogleCredentials.create(new AccessToken("x", new Date(Long.MAX_VALUE / 2))))
				.setProjectId("test-project")
				.setHttpTransport(FirebaseMessagingConfig.fcmHttpTransport())
				.build());

		var originalTransport = original.getOptions().getHttpTransport(); // 재생성하면 original 은 삭제된다
		FirebaseMessaging first = FirebaseMessagingConfig.rebuildMessaging();
		FirebaseMessaging second = FirebaseMessagingConfig.rebuildMessaging();

		assertThat(first).isNotNull().isNotSameAs(second);
		// fcm-1 은 fcm-2 가 만들어질 때 옛 앱으로 정리 대상이 된다(백그라운드라 타이밍은 단정하지 않는다).
		assertThat(FirebaseApp.getApps()).extracting(FirebaseApp::getName).contains("fcm-2");
		assertThat(FirebaseApp.getInstance("fcm-2").getOptions().getProjectId()).isEqualTo("test-project");
		assertThat(FirebaseApp.getInstance("fcm-2").getOptions().getHttpTransport())
				.isNotSameAs(originalTransport);
	}
}
