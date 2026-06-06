package com.toy.nar.api.mobile.rating;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.mobile.rating.MobileLivePlayerRatingService;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingDetailResponse;
import com.toy.nar.app.mobile.rating.dto.LivePlayerRatingListResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileLivePlayerRatingControllerTest {

	private MobileLivePlayerRatingService ratingService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		ratingService = mock(MobileLivePlayerRatingService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new MobileLivePlayerRatingController(ratingService))
				.setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
				.build();
	}

	@Test
	void returnsRatingTabShape() throws Exception {
		when(ratingService.getRatings("game-1", "ALL", null))
				.thenReturn(new LivePlayerRatingListResponse(
						"game-1",
						true,
						List.of(new LivePlayerRatingListResponse.TeamRatingSummary("Red", "T1", 4.5, 23)),
						List.of(new LivePlayerRatingListResponse.PlayerRatingSummary(
								1, 10L, "Faker", "faker.png", "Red", "mid", "Ahri", 4.5, 23, null))));

		mockMvc.perform(get("/api/mobile/live/games/game-1/ratings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rateable").value(true))
				.andExpect(jsonPath("$.teams[0].averageRating").value(4.5))
				.andExpect(jsonPath("$.players[0].playerName").value("Faker"));
	}

	@Test
	void savesAndDeletesMyRating() throws Exception {
		when(ratingService.save(any(), any(), any(), any()))
				.thenReturn(new LivePlayerRatingDetailResponse.MyRating(1L, 5, "좋은 경기"));

		mockMvc.perform(put("/api/mobile/live/games/game-1/participants/1/my-rating")
						.contentType("application/json")
						.content("{\"rating\":5,\"comment\":\"좋은 경기\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rating").value(5));

		mockMvc.perform(delete("/api/mobile/live/games/game-1/participants/1/my-rating"))
				.andExpect(status().isNoContent());

		verify(ratingService).delete("game-1", 1, null);
	}
}
