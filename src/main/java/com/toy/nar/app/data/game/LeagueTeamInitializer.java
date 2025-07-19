package com.toy.nar.app.data.game;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.game.entity.LeagueTeam;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.LeagueTeamRepository;
import com.toy.nar.domain.participant.entity.Team;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeagueTeamInitializer {

	private final GameParticipantRepository gameParticipantRepository;
	private final LeagueTeamRepository leagueTeamRepository;

	@Transactional
	public void initializeLeagueTeams() {
		if (leagueTeamRepository.count() > 0) {
			log.info("LeagueTeam data already exists. Skipping initialization.");
			return;
		}

		log.info("Initializing LeagueTeam data...");

		List<Object[]> leagueTeamPairs = gameParticipantRepository.findDistinctLeagueTeamPairs();
		List<LeagueTeam> leagueTeamsToSave = new ArrayList<>();

		for (Object[] pair : leagueTeamPairs) {
			League league = (League) pair[0];
			Team team = (Team) pair[1];

			LeagueTeam leagueTeam = LeagueTeam.builder()
				.league(league)
				.team(team)
				.build();
			leagueTeamsToSave.add(leagueTeam);
		}

		// 배치로 저장 (더 효율적)
		leagueTeamRepository.saveAll(leagueTeamsToSave);

		log.info("LeagueTeam initialization completed. Created {} records.", leagueTeamsToSave.size());
	}
}