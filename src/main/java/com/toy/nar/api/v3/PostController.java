package com.toy.nar.api.v3;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.community.InvenParserService;
import com.toy.nar.app.community.OpggParserService;
import com.toy.nar.app.community.dto.InvenPostDto;
import com.toy.nar.app.community.dto.OpggPostDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PostController {

	private final OpggParserService opggParserService;
	private final InvenParserService invenParserService;

	@GetMapping("/test-opgg")
	public List<OpggPostDto> testOpgg() {
		return opggParserService.parseEsportsPosts();
	}

	@GetMapping("/test-inven")
	public List<InvenPostDto> testInven() {
		return invenParserService.parseInvenPosts();
	}
}
