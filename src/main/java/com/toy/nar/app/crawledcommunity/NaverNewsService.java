package com.toy.nar.app.crawledcommunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.crawledcommunity.dto.NaverNewsDto;
import com.toy.nar.app.crawledcommunity.repository.NewsPost;
import com.toy.nar.app.crawledcommunity.repository.NewsPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverNewsService {

	private static final int CRAWL_TIMEOUT_MS = 5000;

	private final ObjectMapper objectMapper;
	private final NewsPostRepository newsPostRepository;
	private final TransactionTemplate transactionTemplate;

	/** 네이버 API 호출은 트랜잭션 밖, DB 쓰기 루프만 트랜잭션 안. */
	public void syncNaverNews(String sortType) {
		List<NaverNewsDto> newsList = parseNaverNews(sortType);
		transactionTemplate.executeWithoutResult(status -> {
			for (NaverNewsDto dto : newsList) {
				saveOrUpdate(dto);
			}
		});
		log.info("Synced {} Naver news ({})", newsList.size(), sortType);
	}

	public List<NaverNewsDto> parseNaverNews(String sortType) {
		List<NaverNewsDto> newsList = new ArrayList<>();
		String today = LocalDate.now().toString(); // yyyy-MM-dd
		
		String url = "https://esports-api.game.naver.com/service/v1/news/list"
			+ "?newsType=lol&page=1&pageSize=20&day=" + today + "&sort=" + sortType;

		try {
			String jsonResponse = Jsoup.connect(url)
				.ignoreContentType(true)
				.userAgent("Mozilla/5.0")
				// Jsoup 기본 타임아웃은 30초 — 외부 사이트가 멈추면 스케줄러가 그만큼 매달린다.
				.timeout(CRAWL_TIMEOUT_MS)
				.execute()
				.body();

			JsonNode root = objectMapper.readTree(jsonResponse);
			JsonNode content = root.path("content");

			if (content.isArray()) {
				for (JsonNode node : content) {
					newsList.add(NaverNewsDto.builder()
						.title(node.path("title").asText())
						.subContent(node.path("subContent").asText())
						.thumbnail(node.path("thumbnail").asText())
						.postUrl(normalizeUrl(node.path("linkUrl").asText()))
						.officeName(node.path("officeName").asText())
						.createdAt(node.path("createdAt").asLong())
						.build());
				}
			}
		} catch (Exception e) {
			log.error("Failed to parse Naver news", e);
		}
		return newsList;
	}

	private String normalizeUrl(String url) {
		if (url == null) return "";
		int queryIndex = url.indexOf("?");
		if (queryIndex != -1) {
			return url.substring(0, queryIndex);
		}
		return url;
	}

	private void saveOrUpdate(NaverNewsDto dto) {
		// 1. URL로 먼저 찾기
		Optional<NewsPost> existingByUrl = newsPostRepository.findByPostUrl(dto.getPostUrl());
		
		// 2. URL로 없으면 제목으로 찾기 (혹시 모를 중복 방지)
		Optional<NewsPost> existing = existingByUrl.isPresent() ? existingByUrl : newsPostRepository.findByTitle(dto.getTitle());

		LocalDateTime createdAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(dto.getCreatedAt()), ZoneId.systemDefault());

		if (existing.isPresent()) {
			// 존재하면 업데이트
			existing.get().update(dto.getTitle(), dto.getSubContent(), dto.getThumbnail());
		} else {
			// 둘 다 없으면 신규 저장
			newsPostRepository.save(NewsPost.builder()
				.title(dto.getTitle())
				.subContent(dto.getSubContent())
				.thumbnail(dto.getThumbnail())
				.postUrl(dto.getPostUrl())
				.officeName(dto.getOfficeName())
				.createdAt(createdAt)
				.lastUpdated(LocalDateTime.now())
				.build());
		}
	}
}
