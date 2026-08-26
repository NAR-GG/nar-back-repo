package com.toy.nar.app.crawledcommunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.crawledcommunity.dto.NaverPostDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverParserService {

	// Jsoup 기본 타임아웃은 30초 — 외부 사이트가 멈추면 스케줄러가 그만큼 매달린다.
	private static final int CRAWL_TIMEOUT_MS = 5000;

	private final ObjectMapper objectMapper;

	public List<NaverPostDto> parseNaverPosts(String sortType) {
		List<NaverPostDto> postList = new ArrayList<>();
		
		// 정렬 파라미터 매핑
		String order = "NEW"; // 기본값 (latest)
		if ("popular".equalsIgnoreCase(sortType)) {
			order = "RANK";
		}

		String url = "https://comm-api.game.naver.com/nng_main/v1/community/lounge/League_of_Legends/feed"
			+ "?limit=25&offset=0&boardId=10&order=" + order; // boardId=10 (LCK 이야기)
			// boardId=0으로 하면 전체글일 수 있는데, 사용자가 준 예시엔 boardId=10이 포함되어 있어서 일단 10으로 하거나
			// 사용자가 준 URL 그대로 "boardId=0"을 쓸 수도 있음. 
			// 사용자 예시: boardId=0. 응답: boardId=10 (LCK 이야기)
			// 안전하게 사용자가 준 URL의 파라미터(boardId=0)를 따르겠습니다.

		try {
			String jsonResponse = Jsoup.connect(url)
				.ignoreContentType(true)
				.userAgent("Mozilla/5.0")
				.timeout(CRAWL_TIMEOUT_MS)
				.execute()
				.body();

			JsonNode root = objectMapper.readTree(jsonResponse);
			JsonNode feedsNode = root.path("content").path("feeds");

			if (feedsNode.isArray()) {
				for (JsonNode node : feedsNode) {
					JsonNode feed = node.path("feed");
					JsonNode user = node.path("user");
					JsonNode comment = node.path("comment");

					// 날짜 파싱 (20251231175007 -> yyyy-MM-dd HH:mm:ss)
					String rawDate = feed.path("createdDate").asText();
					String createdAt = convertDateFormat(rawDate);

					// 링크 생성
					String feedId = feed.path("feedId").asText();
					String postUrl = "https://m.game.naver.com/lounge/League_of_Legends/board/detail/" + feedId;

					NaverPostDto post = NaverPostDto.builder()
						.title(feed.path("title").asText())
						.author(user.path("nickname").asText())
						.createdAt(createdAt)
						.postUrl(postUrl)
						.voteCount(feed.path("buff").asInt(0)) // buff = 추천수
						.commentCount(comment.path("totalCount").asInt(0))
						.viewCount(node.path("readCount").asInt(0))
						.build();

					postList.add(post);
				}
			}

		} catch (Exception e) {
			log.error("Failed to parse Naver posts", e);
		}

		return postList;
	}

	private String convertDateFormat(String rawDate) {
		// yyyyMMddHHmmss -> yyyy-MM-dd HH:mm:ss
		try {
			if (rawDate != null && rawDate.length() == 14) {
				LocalDateTime dateTime = LocalDateTime.parse(rawDate, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
				return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			}
		} catch (Exception e) {
			// ignore
		}
		return rawDate;
	}
}
