package com.toy.nar.api.v3;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.community.InvenParserService;
import com.toy.nar.app.community.OpggParserService;
import com.toy.nar.app.community.dto.InvenPostDto;
import com.toy.nar.app.community.dto.OpggPostDto;

import com.toy.nar.app.community.repository.CommunityPost;
import com.toy.nar.app.community.repository.NewsPost;
import com.toy.nar.app.community.CommunityService;
import com.toy.nar.app.community.NaverParserService;
import com.toy.nar.app.community.dto.NaverPostDto;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PostController {

	private final OpggParserService opggParserService;
	private final InvenParserService invenParserService;
	private final NaverParserService naverParserService;
	private final CommunityService communityService;

	@PostMapping("/api/community/sync")
	public String syncCommunities(@org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "latest") String sort) {
		communityService.syncAll(sort);
		return "Synced all communities and news with sort: " + sort;
	}

	@GetMapping("/api/community/top5")
	public List<CommunityPost> getTop5Posts(@org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "latest") String sort) {
		return communityService.getTop5Posts(sort);
	}

	@GetMapping("/api/community/news")
	public List<NewsPost> getTop5News() {
		return communityService.getTop5News();
	}

	@GetMapping("/test-opgg")
	public List<OpggPostDto> testOpgg(@org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "latest") String sort) {
		return opggParserService.parseEsportsPosts(sort);
	}

	@GetMapping("/test-inven")
	public List<InvenPostDto> testInven(@org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "latest") String sort) {
		return invenParserService.parseInvenPosts(sort);
	}

	@GetMapping("/test-naver")
	public List<NaverPostDto> testNaver(@org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "latest") String sort) {
		return naverParserService.parseNaverPosts(sort);
	}
}
