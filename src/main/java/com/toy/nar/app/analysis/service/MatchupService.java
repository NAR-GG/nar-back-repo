package com.toy.nar.app.analysis.service;

import com.toy.nar.app.analysis.dto.CombinationDetailDto;
import com.toy.nar.app.analysis.dto.PageMatchupResponse;
import com.toy.nar.common.util.NameNormalizer;
import com.toy.nar.domain.combination.strategy.MultiCombinationFilterDto;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchupService {

	private final GameParticipantRepository gameParticipantRepository;

	/**
	 * 1v1 매치업 통계를 조회하는 퍼블릭 메서드.
	 * 역할: 전체 프로세스 조정 (협력자들에게 책임 위임).
	 * 책임: 입력 유효성 검사와 최종 응답 조립.
	 */
	public PageMatchupResponse get1v1MatchupStats(String champion1, String champion2, MultiCombinationFilterDto filter, Pageable pageable) {
		String normalizedChampion1 = NameNormalizer.normalizeChampionName(champion1);
		String normalizedChampion2 = NameNormalizer.normalizeChampionName(champion2);

		// 데이터 조회 책임 위임
		List<GameParticipant> participants = fetchMatchupParticipants(normalizedChampion1, normalizedChampion2, filter);

		if (participants.isEmpty()) {
			return new PageMatchupResponse(0, 0.0, Collections.emptyList(), pageable, false, 0);
		}

		// 그룹화 및 통계 계산 책임 위임
		MatchupStats stats = calculateMatchupStats(participants, normalizedChampion1);

		// 게임 기록 DTO 생성 책임 위임
		List<CombinationDetailDto.GameDetailDto> allGameRecords = buildGameRecords(stats.gameGroups(), normalizedChampion1, normalizedChampion2);

		// 페이징 적용 책임 위임
		PagedResult<CombinationDetailDto.GameDetailDto> pagedResult = applyPaging(allGameRecords, pageable);

		return new PageMatchupResponse(
			(int) stats.totalMatches(),
			stats.winRate(),
			pagedResult.pagedRecords(),
			pageable,
			pagedResult.hasNext(),
			pagedResult.totalCount()
		);
	}

	// --- Private Helper Methods (책임 분리) ---

	/**
	 * 데이터 조회 책임: Repository와 협력하여 필터링된 참가자 목록을 가져옴.
	 */
	private List<GameParticipant> fetchMatchupParticipants(String normalizedChampion1, String normalizedChampion2, MultiCombinationFilterDto filter) {
		return gameParticipantRepository.find1v1MatchupParticipants(
			normalizedChampion1,
			normalizedChampion2,
			filter.getYear(),
			filter.getSplits(),
			filter.getLeagueNames(),
			filter.getTeamNames(),
			filter.getPatch()
		);
	}

	/**
	 * 통계 계산 책임: 참가자 목록을 그룹화하고 총 매치업 수, 승률 계산.
	 * @return MatchupStats 레코드 (불변 객체로 계산 결과 캡슐화)
	 */
	private MatchupStats calculateMatchupStats(List<GameParticipant> participants, String normalizedChampion1) {
		Map<Long, List<GameParticipant>> gameGroups = participants.stream()
			.collect(Collectors.groupingBy(gp -> gp.getGame().getId()));

		long totalMatches = gameGroups.size();

		int winsForChampion1 = (int) gameGroups.values().stream()
			.filter(group -> group.stream()
				.filter(gp -> gp.getChampion().getChampionNameEn().equalsIgnoreCase(normalizedChampion1))
				.findFirst()
				.filter(GameParticipant::getIsWin)
				.isPresent())
			.count();

		double winRate = (totalMatches > 0) ? (winsForChampion1 * 100.0 / totalMatches) : 0.0;

		return new MatchupStats(totalMatches, winRate, gameGroups);
	}

	/**
	 * 게임 기록 DTO 생성 책임: 각 게임 그룹을 DTO로 변환 (최신순 정렬 포함).
	 * createTeamDetail과 createOpponentTeamDetail 헬퍼와 협력.
	 */
	private List<CombinationDetailDto.GameDetailDto> buildGameRecords(
		Map<Long, List<GameParticipant>> gameGroups,
		String normalizedChampion1,
		String normalizedChampion2
	) {
		return gameGroups.entrySet().stream()
			.sorted(Comparator.comparing(entry -> entry.getValue().get(0).getGame().getActualGameStartTime(), Comparator.reverseOrder()))
			.map(entry -> {
				Long gameId = entry.getKey();
				List<GameParticipant> gameParts = entry.getValue();

				Optional<GameParticipant> p1Opt = gameParts.stream()
					.filter(gp -> gp.getChampion().getChampionNameEn().equalsIgnoreCase(normalizedChampion1))
					.findFirst();

				boolean champion1Won = p1Opt.filter(GameParticipant::getIsWin).isPresent();

				// ourTeam: champion1 소속 팀 (헬퍼 메서드 호출)
				CombinationDetailDto.TeamDetailDto ourTeam = p1Opt
					.map(p1 -> createTeamDetail(p1, gameParts))
					.orElse(null);  // null 안전 처리 (필요 시 예외 throw)

				// opponentTeam: champion2 소속 팀 (헬퍼 메서드 호출)
				CombinationDetailDto.TeamDetailDto opponentTeam = createOpponentTeamDetail(gameParts, normalizedChampion2)
					.orElse(null);

				// DTO 생성 (기존 필드 + 1v1 특화 필드)
				return new CombinationDetailDto.GameDetailDto(
					gameId,
					gameParts.get(0).getGame().getActualGameStartTime(),
					gameParts.get(0).getGame().getLeague().getSeasonSplit(),
					gameParts.get(0).getGame().getLeague().getLeagueName(),
					gameParts.get(0).getGame().getPatch(),
					gameParts.get(0).getGame().getGameLengthSeconds(),
					ourTeam,
					opponentTeam,
					Optional.of(champion1Won)
				);
			})
			.collect(Collectors.toList());
	}

	/**
	 * 페이징 적용 책임: 리스트를 페이징하고 결과를 캡슐화.
	 * @return PagedResult 레코드 (불변 객체)
	 */
	private <T> PagedResult<T> applyPaging(List<T> allRecords, Pageable pageable) {
		int from = (int) pageable.getOffset();
		int to = Math.min(from + pageable.getPageSize(), allRecords.size());
		List<T> pagedRecords = allRecords.subList(from, to);
		boolean hasNext = to < allRecords.size();
		long totalCount = allRecords.size();
		return new PagedResult<>(pagedRecords, hasNext, totalCount);
	}

	// --- 헬퍼 메서드: TeamDetailDto 생성 (요청하신 대로 구현) ---

	/**
	 * ourTeam 생성 헬퍼: 주어진 참가자(p1)를 기반으로 팀 상세 DTO 빌드.
	 * 책임: PlayerDetailDto 목록 생성 (Stream 활용).
	 */
	private CombinationDetailDto.TeamDetailDto createTeamDetail(GameParticipant p1, List<GameParticipant> participants) {
		String teamName = p1.getTeam().getName();
		String side = p1.getSide();
		boolean isWin = p1.getIsWin();

		List<CombinationDetailDto.PlayerDetailDto> players = participants.stream()
			.filter(gp -> gp.getTeam().getName().equals(teamName))  // 같은 팀 필터
			.map(gp -> new CombinationDetailDto.PlayerDetailDto(
				gp.getPosition(),
				gp.getChampion().getChampionNameEn(),
				gp.getPlayer().getName()
			))
			.collect(Collectors.toList());

		return new CombinationDetailDto.TeamDetailDto(teamName, side, isWin, players);
	}

	/**
	 * opponentTeam 생성 헬퍼: champion2를 기반으로 상대 팀 상세 DTO 빌드.
	 * 책임: 상대 팀 식별 및 PlayerDetailDto 목록 생성 (Optional 활용으로 null 안전).
	 */
	private Optional<CombinationDetailDto.TeamDetailDto> createOpponentTeamDetail(List<GameParticipant> participants, String normalizedChampion2) {
		return participants.stream()
			.filter(gp -> gp.getChampion().getChampionNameEn().equalsIgnoreCase(normalizedChampion2))
			.findFirst()
			.map(p2 -> {
				String teamName = p2.getTeam().getName();
				String side = p2.getSide();
				boolean isWin = p2.getIsWin();

				List<CombinationDetailDto.PlayerDetailDto> players = participants.stream()
					.filter(gp -> gp.getTeam().getName().equals(teamName))
					.map(gp -> new CombinationDetailDto.PlayerDetailDto(
						gp.getPosition(),
						gp.getChampion().getChampionNameEn(),
						gp.getPlayer().getName()
					))
					.collect(Collectors.toList());

				return new CombinationDetailDto.TeamDetailDto(teamName, side, isWin, players);
			});
	}

	private record MatchupStats(long totalMatches, double winRate, Map<Long, List<GameParticipant>> gameGroups) {}

	private record PagedResult<T>(List<T> pagedRecords, boolean hasNext, long totalCount) {}

}
