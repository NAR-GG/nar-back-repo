package com.toy.nar.app.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.community.dto.OpggPostDto;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OpggParserService {

	private final ObjectMapper objectMapper;

	public OpggParserService(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public List<OpggPostDto> parseEsportsPosts(String sortType) {
		List<OpggPostDto> postList = new ArrayList<>();
		
		// 클라이언트 파라미터(latest) -> OP.GG 파라미터(recent) 매핑
		if (sortType == null || sortType.isEmpty() || "latest".equalsIgnoreCase(sortType)) {
			sortType = "recent";
		}
		
		String url = "https://talk.op.gg/s/lol/esports?sort=" + sortType;

		try {
			// 1. Jsoup으로 HTML 가져오기 (User-Agent 설정은 필수)
			Document doc = Jsoup.connect(url)
				.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
				.get();

			// 2. __NEXT_DATA__ 스크립트 태그 찾기
			String jsonData = doc.getElementById("__NEXT_DATA__").data();

			// 3. Jackson JsonNode로 트리 구조 탐색
			JsonNode root = objectMapper.readTree(jsonData);
			JsonNode posts = root.path("props")
				.path("pageProps")
				.path("posts")
				.path("data");

			if (posts.isArray()) {
				for (JsonNode node : posts) {

					// 4. [추가] 댓글 수 추출 (없으면 0)
					int commentCount = node.path("comment_count").asInt(0);

					// 5. [추가] 조회수 추출 (OP.GG는 hit_count 키 사용)
					int viewCount = node.path("hit_count").asInt(0);

					OpggPostDto post = OpggPostDto.builder()
						.id(node.path("id").asLong())
						.title(node.path("title").asText())
						.author(node.path("user_name").asText())
						.createdAt(node.path("created_at").asText())
						.postUrl("https://talk.op.gg/s/lol/esports/" + node.path("id").asLong())
						// 추가된 필드 매핑
						.voteCount(node.path("vote_score").asInt(0))
						.commentCount(commentCount)
						.viewCount(viewCount)
						.build();

					postList.add(post);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return postList;
	}
}