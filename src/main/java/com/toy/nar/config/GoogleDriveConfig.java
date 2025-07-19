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

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
public class GoogleDriveConfig {

	private static final String APPLICATION_NAME = "NAR Game Data Importer";
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final String SERVICE_ACCOUNT_KEY_PATH = "src/main/resources/service-account-key.json";

	@Bean
	public Drive googleDrive() throws GeneralSecurityException, IOException {
		final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

		ServiceAccountCredentials credentials = (ServiceAccountCredentials)ServiceAccountCredentials
			.fromStream(new FileInputStream(SERVICE_ACCOUNT_KEY_PATH))
			.createScoped(Collections.singletonList("https://www.googleapis.com/auth/drive.readonly"));

		return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
			.setApplicationName(APPLICATION_NAME)
			.build();
	}
}