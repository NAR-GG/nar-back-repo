package com.toy.nar.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
public class GoogleDriveConfig {

	private static final String APPLICATION_NAME = "NAR Game Data Importer";
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final String SERVICE_ACCOUNT_KEY_FILENAME = "service-account-key.json";
	private static final String ENV_VAR_NAME = "GOOGLE_SERVICE_ACCOUNT_KEY";

	@Bean
	public Drive googleDrive() throws GeneralSecurityException, IOException {
		final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

		// 1. 환경 변수 우선 시도 (도커 배포 시 사용)
		String serviceAccountKeyJson = System.getenv(ENV_VAR_NAME);
		if (serviceAccountKeyJson != null && !serviceAccountKeyJson.isEmpty()) {
			return buildDriveFromJsonString(serviceAccountKeyJson);
		}

		// 2. 환경 변수 없으면 클래스패스 파일 시도 (로컬 개발 시 사용)
		InputStream credentialsStreamFromFile = getClass().getClassLoader().getResourceAsStream(SERVICE_ACCOUNT_KEY_FILENAME);
		if (credentialsStreamFromFile != null) {
			String originalJsonString = new String(credentialsStreamFromFile.readAllBytes(), StandardCharsets.UTF_8);
			return buildDriveFromJsonString(originalJsonString);
		}

		// 3. 둘 다 없으면 에러
		throw new IOException("Service account key not found: Set environment variable " + ENV_VAR_NAME + " or provide file " + SERVICE_ACCOUNT_KEY_FILENAME + " in classpath");
	}

	/**
	 * 공통 헬퍼: JSON 문자열로부터 Drive 인스턴스 빌드 (중복 제거).
	 */
	private Drive buildDriveFromJsonString(String jsonString) throws IOException, GeneralSecurityException {
		// 이스케이프 문자 수정 (기존 로직 유지)
		String correctedJsonString = jsonString.replace("\\n", "\n");
		InputStream correctedCredentialsStream = new ByteArrayInputStream(correctedJsonString.getBytes(StandardCharsets.UTF_8));

		ServiceAccountCredentials credentials = (ServiceAccountCredentials) ServiceAccountCredentials
			.fromStream(correctedCredentialsStream)
			.createScoped(Collections.singletonList("https://www.googleapis.com/auth/drive.readonly"));

		return new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, new HttpCredentialsAdapter(credentials))
			.setApplicationName(APPLICATION_NAME)
			.build();
	}
}
