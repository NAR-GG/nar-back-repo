package com.toy.nar.app.data.game.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.toy.nar.domain.game.repository.LeagueTeamRepository;
import com.toy.nar.domain.participant.entity.Team;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeagueService {

	private final LeagueTeamRepository leagueTeamRepository;

	public List<Team> getTeamsByLeague(String leagueName, Integer seasonYear, String seasonSplit) {
		return leagueTeamRepository.findTeamsByLeagueParams(leagueName, seasonYear, seasonSplit);
	}
}
