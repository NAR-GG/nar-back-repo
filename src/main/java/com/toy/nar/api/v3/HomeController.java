package com.toy.nar.api.v3;

import com.toy.nar.app.analysis.dto.ChampionAnalysisResponse;
import com.toy.nar.app.analysis.dto.ChampionStatsDto;
import com.toy.nar.app.analysis.service.ChampionAnalysisService;
import com.toy.nar.app.community.CommunityService;
import com.toy.nar.app.community.repository.CommunityPost;
import com.toy.nar.app.community.repository.NewsPost;
import com.toy.nar.app.lolesports.LeagueMatchService;
import com.toy.nar.app.lolesports.MatchResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "6. Home API", description = "홈 화면용 데이터 제공 API (경기 일정, 커뮤니티, 뉴스, 챔피언 통계)")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

	private final LeagueMatchService leagueMatchService;
	private final CommunityService communityService;
	private final ChampionAnalysisService championAnalysisService;

	@Operation(summary = "경기 일정 조회 (날짜별)", description = "특정 날짜의 모든 리그 경기 일정을 조회합니다. 날짜 미입력 시 전체 리그의 최신 경기를 반환합니다.")
	@GetMapping("/schedule")
	public List<MatchResultDto> getSchedule(
		@Parameter(description = "조회할 날짜 (YYYY-MM-DD)", example = "2026-01-04") 
		@RequestParam(required = false) String date,
		@Parameter(description = "리그 이름 (ALL, LCK, LPL 등)", example = "ALL") 
		@RequestParam(required = false, defaultValue = "ALL") String league) {
		
		return leagueMatchService.getMatchesFromDb(league, date).getMatches();
	}

	@Operation(summary = "커뮤니티 TOP 5 조회", description = "통합 커뮤니티(OP.GG, Inven, Naver)의 상위 5개 게시글을 조회합니다.")
	@GetMapping("/community")
	public List<CommunityPost> getTop5CommunityPosts(
		@Parameter(description = "정렬 기준 (latest: 최신순, popular: 인기순)", example = "latest") 
		@RequestParam(required = false, defaultValue = "latest") String sort) {
		return communityService.getTop5Posts(sort);
	}

	@Operation(summary = "최신 뉴스 TOP 5 조회", description = "네이버 이스포츠 뉴스의 최신 상위 5개 기사를 조회합니다.")
	@GetMapping("/news")
	public List<NewsPost> getTop5News() {
		return communityService.getTop5News();
	}

	@Operation(summary = "최근 패치 모스트 챔피언 TOP 5 조회", description = "LCK 리그의 가장 최신 패치 버전에서 픽률이 높은 상위 5개 챔피언의 통계(승률, 경기수)를 조회합니다.")
	@GetMapping("/champion/top5")
	public ChampionAnalysisResponse getTop5Champions() {
		return championAnalysisService.getMostPlayedChampionsByLatestPatch();
	}
}
