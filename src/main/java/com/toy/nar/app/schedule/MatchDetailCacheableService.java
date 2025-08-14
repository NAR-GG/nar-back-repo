package com.toy.nar.app.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.toy.nar.app.schedule.dto.MatchDetailResponseDto;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchDetailCacheableService {

	private final MatchDetailFinder matchDetailFinder;

	@Cacheable(value = "todayMatchDetails", key = "#matchId", unless = "#result == null")
	public MatchDetailResponseDto getTodayMatchDetail(String matchId) {
		log.info("DB에서 오늘 경기 상세 정보를 조회합니다: matchId={}", matchId);
		return matchDetailFinder.findMatchDetail(matchId);
	}

	@Cacheable(value = "matchDetails", key = "#matchId", unless = "#result == null")
	public MatchDetailResponseDto getPastMatchDetail(String matchId) {
		log.info("DB에서 과거 경기 상세 정보를 조회합니다: matchId={}", matchId);
		return matchDetailFinder.findMatchDetail(matchId);
	}
}