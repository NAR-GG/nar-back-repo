package com.toy.nar.app.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.analysis.dto.PlayerCardListResponse;
import com.toy.nar.domain.game.repository.GameParticipantRepository;

@ExtendWith(MockitoExtension.class)
class PlayerCardServiceTest {

	@Mock
	private GameParticipantRepository gameParticipantRepository;

	private PlayerCardService playerCardService;

	@BeforeEach
	void setUp() {
		playerCardService = new PlayerCardService(gameParticipantRepository, new ObjectMapper());
	}

	@Test
	@DisplayName("선수 카드 목록은 필터 기준으로 조회되고 모스트 챔피언은 상위 3개만 반환한다")
	void getPlayerCards_returnsCardListWithTop3Champions() {
		when(gameParticipantRepository.countDistinctPlayersByFilter("LCK", 2026, "Round 3-5", "14.1", null))
				.thenReturn(2L);

		when(gameParticipantRepository.findPlayerCardSummariesByFilter("LCK", 2026, "Round 3-5", "14.1", null, 20, 0))
				.thenReturn(List.of(
						new Object[] { 1L, "Faker", "/images/players/faker.webp", "이상혁", "1996-05-07",
								"[{\"riotId\":\"Hide on bush #KR1\",\"tier\":\"Challenger 1200LP\"}]",
								"mid", "T1", "/images/teams/T1.webp", 1L, 30L, 120L, 40L, 210L, 441.7, 958.2 },
						new Object[] { 2L, "Keria", "/images/players/keria.webp", "류민석", "2002-10-14",
								"[{\"riotId\":\"Keria #KR1\",\"tier\":\"Master\"}]",
								"sup", "T1", "/images/teams/T1.webp", 1L, 30L, 40L, 60L, 330L, 320.5, 410.3 }));

		when(gameParticipantRepository.findPlayerMostChampionsByFilter(
				List.of(1L, 2L), "LCK", 2026, "Round 3-5", "14.1", null))
				.thenReturn(List.of(
						new Object[] { 1L, 101L, "아리", "Ahri", "ahri.png", 10L, 7L },
						new Object[] { 1L, 102L, "아지르", "Azir", "azir.png", 9L, 6L },
						new Object[] { 1L, 103L, "요네", "Yone", "yone.png", 8L, 5L },
						new Object[] { 1L, 104L, "탈리야", "Taliyah", "taliyah.png", 4L, 3L },
						new Object[] { 2L, 201L, "레나타", "Renata", "renata.png", 12L, 8L }));

		PlayerCardListResponse response = playerCardService.getPlayerCards(
				"LCK", 2026, "Round 3-5", "14.1", "ALL", 1, 20);

		assertThat(response.getLeagueName()).isEqualTo("LCK");
		assertThat(response.getAppliedFilter().getSide()).isEqualTo("ALL");
		assertThat(response.getTotalCount()).isEqualTo(2L);
		assertThat(response.getTotalPages()).isEqualTo(1);
		assertThat(response.getPlayers()).hasSize(2);

		assertThat(response.getPlayers().get(0).getPlayerName()).isEqualTo("Faker");
		assertThat(response.getPlayers().get(0).getProfile().getPosition()).isEqualTo("MID");
		assertThat(response.getPlayers().get(0).getProfile().getSummonerName()).isEqualTo("Hide on bush #KR1");
		assertThat(response.getPlayers().get(0).getProfile().getSoloRankTier()).isEqualTo("Challenger 1200LP");
		assertThat(response.getPlayers().get(0).getMostChampions()).hasSize(3);
		assertThat(response.getPlayers().get(0).getMostChampions().get(0).getWinRatePct()).isEqualTo(70.0);
		assertThat(response.getPlayers().get(0).getMostChampions().get(0).getChampionLoadingImageUrl())
				.isEqualTo("https://ddragon.leagueoflegends.com/cdn/img/champion/loading/ahri_0.jpg");
		assertThat(response.getPlayers().get(0).getProfile().getKda()).isEqualTo(8.25);
		assertThat(response.getPlayers().get(0).getProfile().getGpm()).isEqualTo(441.7);
		assertThat(response.getPlayers().get(0).getProfile().getDpm()).isEqualTo(958.2);

		verify(gameParticipantRepository).countDistinctPlayersByFilter("LCK", 2026, "Round 3-5", "14.1", null);
		verify(gameParticipantRepository).findPlayerCardSummariesByFilter("LCK", 2026, "Round 3-5", "14.1", null, 20, 0);
	}
}
