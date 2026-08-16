package com.toy.nar.api.mobile.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.mobile.subscription.MobilePlayerSubscriptionService;
import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionPageResponse;
import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobilePlayerSubscriptionControllerTest {

	private MobilePlayerSubscriptionService subscriptionService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		subscriptionService = mock(MobilePlayerSubscriptionService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new MobilePlayerSubscriptionController(subscriptionService))
				.setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
				.build();
	}

	@Test
	void returnsSubscriptionsAndAvailablePlayers() throws Exception {
		PlayerSubscriptionResponse faker = response(true);
		when(subscriptionService.getSubscriptions(null)).thenReturn(List.of(faker));
		when(subscriptionService.getAvailablePlayers(null, "Faker", 1L, 0, 20))
				.thenReturn(new PlayerSubscriptionPageResponse(List.of(faker), 0, 20, 1, 1));

		mockMvc.perform(get("/api/mobile/me/player-subscriptions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].playerName").value("Faker"))
				.andExpect(jsonPath("$[0].teamCode").value("T1"));

		mockMvc.perform(get("/api/mobile/me/player-subscriptions/available-players")
						.param("query", "Faker")
						.param("teamId", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].subscribed").value(true))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void subscribesAndDeletes() throws Exception {
		when(subscriptionService.subscribe(null, 10L)).thenReturn(response(true));

		mockMvc.perform(post("/api/mobile/me/player-subscriptions")
						.contentType("application/json")
						.content("{\"playerId\":10}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.playerId").value(10))
				.andExpect(jsonPath("$.subscribed").value(true));

		mockMvc.perform(delete("/api/mobile/me/player-subscriptions/10"))
				.andExpect(status().isNoContent());

		verify(subscriptionService).delete(null, 10L);
	}

	@Test
	void validatesPlayerId() throws Exception {
		mockMvc.perform(post("/api/mobile/me/player-subscriptions")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	private PlayerSubscriptionResponse response(boolean subscribed) {
		return new PlayerSubscriptionResponse(
				10L,
				"Faker",
				"faker.png",
				"mid",
				1L,
				"T1",
				"T1",
				"t1.png",
				subscribed,
				true,
				false);
	}
}
