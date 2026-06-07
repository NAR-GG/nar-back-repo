package com.toy.nar.api.mobile.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.mobile.notification.MobileTeamNotificationService;
import com.toy.nar.app.mobile.notification.dto.TeamNotificationSubscriptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileTeamNotificationControllerTest {

	private MobileTeamNotificationService notificationService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		notificationService = mock(MobileTeamNotificationService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new MobileTeamNotificationController(notificationService))
				.setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
				.build();
	}

	@Test
	void returnsSubscriptionsAndAvailableTeams() throws Exception {
		var subscription = response(true, true, true, false);
		when(notificationService.getSubscriptions(null)).thenReturn(List.of(subscription));
		when(notificationService.getAvailableTeams(null)).thenReturn(List.of(subscription));

		mockMvc.perform(get("/api/mobile/me/notification-subscriptions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].teamCode").value("T1"))
				.andExpect(jsonPath("$[0].favoriteTeam").value(true));

		mockMvc.perform(get("/api/mobile/me/notification-subscriptions/available-teams"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].subscribed").value(true));
	}

	@Test
	void subscribesUpdatesAndDeletes() throws Exception {
		when(notificationService.subscribe(null, 1L))
				.thenReturn(response(true, true, true, false));
		when(notificationService.update(any(), any(), any()))
				.thenReturn(response(true, false, true, true));

		mockMvc.perform(post("/api/mobile/me/notification-subscriptions")
						.contentType("application/json")
						.content("{\"teamId\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.setStartEnabled").value(true));

		mockMvc.perform(put("/api/mobile/me/notification-subscriptions/1")
						.contentType("application/json")
						.content("""
								{
								  "setStartEnabled": false,
								  "setEndEnabled": true,
								  "liveEventEnabled": true
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.setStartEnabled").value(false))
				.andExpect(jsonPath("$.liveEventEnabled").value(true));

		mockMvc.perform(delete("/api/mobile/me/notification-subscriptions/1"))
				.andExpect(status().isNoContent());

		verify(notificationService).delete(null, 1L);
	}

	@Test
	void validatesRequiredRequestFields() throws Exception {
		mockMvc.perform(post("/api/mobile/me/notification-subscriptions")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/mobile/me/notification-subscriptions/1")
						.contentType("application/json")
						.content("{\"setStartEnabled\":true}"))
				.andExpect(status().isBadRequest());
	}

	private TeamNotificationSubscriptionResponse response(
			boolean subscribed,
			boolean setStartEnabled,
			boolean setEndEnabled,
			boolean liveEventEnabled) {
		return new TeamNotificationSubscriptionResponse(
				1L,
				"T1",
				"T1",
				"t1.png",
				true,
				subscribed,
				setStartEnabled,
				setEndEnabled,
				liveEventEnabled);
	}
}
