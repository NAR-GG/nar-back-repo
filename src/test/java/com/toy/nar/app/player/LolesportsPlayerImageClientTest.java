package com.toy.nar.app.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LolesportsPlayerImageClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private JsonNode json(String body) throws Exception {
		return objectMapper.readTree(body);
	}

	@Test
	void 선수_이름_소문자_키로_이미지_URL을_추출한다() throws Exception {
		JsonNode root = json("""
				{"data":{"teams":[
					{"name":"T1","homeLeague":{"name":"LCK"},"players":[
						{"summonerName":"Faker","image":"http://static.lolesports.com/players/faker.png"}
					]}
				]}}
				""");

		Map<String, String> images = LolesportsPlayerImageClient.extractPlayerImages(root);

		assertThat(images).containsEntry("faker", "http://static.lolesports.com/players/faker.png");
	}

	@Test
	void 기본_헤드샷_이미지는_제외한다() throws Exception {
		JsonNode root = json("""
				{"data":{"teams":[
					{"name":"T1","homeLeague":{"name":"LCK"},"players":[
						{"summonerName":"Rookie1","image":"http://static.lolesports.com/players/default-headshot.png"},
						{"summonerName":"NoImage"}
					]}
				]}}
				""");

		Map<String, String> images = LolesportsPlayerImageClient.extractPlayerImages(root);

		assertThat(images).isEmpty();
	}

	@Test
	void 이름_충돌시_LCK_소속팀_이미지를_우선한다() throws Exception {
		JsonNode root = json("""
				{"data":{"teams":[
					{"name":"Foreign Team","homeLeague":{"name":"LEC"},"players":[
						{"summonerName":"Duro","image":"http://static.lolesports.com/players/duro-lec.png"}
					]},
					{"name":"DN SOOPers","homeLeague":{"name":"LCK"},"players":[
						{"summonerName":"Duro","image":"http://static.lolesports.com/players/duro-lck.png"}
					]}
				]}}
				""");

		Map<String, String> images = LolesportsPlayerImageClient.extractPlayerImages(root);

		assertThat(images).containsEntry("duro", "http://static.lolesports.com/players/duro-lck.png");
	}

	@Test
	void LCK_외_리그_선수도_포함한다_공백은_트림한다() throws Exception {
		JsonNode root = json("""
				{"data":{"teams":[
					{"name":"LYON","homeLeague":{"name":"LTA North"},"players":[
						{"summonerName":"Berserker ","image":"http://static.lolesports.com/players/berserker.png"}
					]}
				]}}
				""");

		Map<String, String> images = LolesportsPlayerImageClient.extractPlayerImages(root);

		assertThat(images).containsEntry("berserker", "http://static.lolesports.com/players/berserker.png");
	}
}
