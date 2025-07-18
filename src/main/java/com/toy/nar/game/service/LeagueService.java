package com.toy.nar.game.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.toy.nar.game.repository.LeagueTeamRepository;
import com.toy.nar.participant.entity.Team;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeagueService {

	private final LeagueTeamRepository leagueTeamRepository;

	public List<Team> getTeamsByLeague(String leagueName, Integer seasonYear, String seasonSplit) {
		return leagueTeamRepository.findTeamsByLeagueParams(leagueName, seasonYear, seasonSplit);
	}
}
