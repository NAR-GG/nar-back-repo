package com.toy.nar.api.mobile.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.mobile.device.MobileDeviceService;
import com.toy.nar.app.mobile.device.dto.MobileDeviceResponse;
import com.toy.nar.domain.member.entity.MobileDevicePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileDeviceControllerTest {

	private MobileDeviceService deviceService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		deviceService = mock(MobileDeviceService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new MobileDeviceController(deviceService))
				.setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
				.build();
	}

	@Test
	void registersAndDeactivatesDevice() throws Exception {
		when(deviceService.register(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new MobileDeviceResponse(3L, MobileDevicePlatform.ANDROID, true));

		mockMvc.perform(post("/api/mobile/me/devices")
						.contentType("application/json")
						.content("{\"fcmToken\":\"token\",\"platform\":\"ANDROID\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deviceId").value(3))
				.andExpect(jsonPath("$.active").value(true));

		mockMvc.perform(delete("/api/mobile/me/devices/3"))
				.andExpect(status().isNoContent());

		verify(deviceService).deactivate(null, 3L);
	}

	@Test
	void validatesRegistrationRequest() throws Exception {
		mockMvc.perform(post("/api/mobile/me/devices")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isBadRequest());
	}
}
