package com.toy.nar.api.v3;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.community.InvenParserService;
import com.toy.nar.app.community.OpggParserService;
import com.toy.nar.app.community.dto.InvenPostDto;
import com.toy.nar.app.community.dto.OpggPostDto;

import com.toy.nar.app.community.NaverParserService;
import com.toy.nar.app.community.dto.NaverPostDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PostController {

	private final OpggParserService opggParserService;
	private final InvenParserService invenParserService;
	private final NaverParserService naverParserService;

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
