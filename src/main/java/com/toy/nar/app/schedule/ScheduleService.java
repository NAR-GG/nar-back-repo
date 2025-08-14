package com.toy.nar.app.schedule;

import com.toy.nar.app.schedule.dto.*;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto.PlayerPickDto;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto.TeamPicksDto;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.GameRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

	private final ScheduleCacheableService scheduleCacheableService;
	private final MatchDetailCacheableService matchDetailCacheableService;
	private final GameRepository gameRepository;

	/**
	 * 일정 조회 공개 메서드.
	 * 날짜에 따라 오늘 또는 과거 일정을 조회하는 캐시 서비스를 호출합니다.
	 */
	public ScheduleResponseDto getDailySchedule(LocalDate date) {
		if (date.isEqual(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
			return scheduleCacheableService.getTodaySchedule(date);
		}
		return scheduleCacheableService.getPastSchedule(date);
	}

	/**
	 * 매치 상세 정보 조회 서비스
	 */
	@Transactional(readOnly = true)
	public MatchDetailResponseDto getMatchDetail(String matchId) {
		Set<Long> gameIds = decodeMatchId(matchId);
		if (gameIds.isEmpty()) return null;

		// 대표 게임 ID 하나로 날짜를 확인합니다.
		Long representativeGameId = gameIds.iterator().next();
		LocalDate matchDate = gameRepository.findById(representativeGameId)
			.map(game -> game.getScheduledGameStartTime().atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate())
			.orElse(LocalDate.now());

		if (matchDate.isEqual(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
			return matchDetailCacheableService.getTodayMatchDetail(matchId);
		}
		return matchDetailCacheableService.getPastMatchDetail(matchId);
	}

	private Set<Long> decodeMatchId(String matchId) {
		byte[] decodedBytes = Base64.getDecoder().decode(matchId);
		String[] idStrings = new String(decodedBytes).split(",");
		return Arrays.stream(idStrings).map(Long::parseLong).collect(Collectors.toSet());
	}
}