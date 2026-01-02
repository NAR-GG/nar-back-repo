package com.toy.nar.app.community;

import com.toy.nar.app.community.dto.InvenPostDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InvenParserService {

	public List<InvenPostDto> parseInvenPosts() {
		List<InvenPostDto> postList = new ArrayList<>();
		// 롤 인벤 e스포츠 게시판
		String url = "https://m.inven.co.kr/board/lol/4625";

		try {
			Document doc = Jsoup.connect(url)
				.userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
				.get();

			// 게시글 리스트 아이템 추출
			Elements posts = doc.select("section.mo-board-list li.list");

			for (Element post : posts) {
				// 1. 제목 추출
				String title = post.select("span.subject").text();
				if (title.isEmpty()) continue; // 공지사항 등 예외 처리

				// 2. 작성자 (공백 제거)
				String author = post.select("span.layerNickName").text().trim();

				// 3. 작성시간
				String createdAt = post.select("span.time").text();

				// 4. 추천수 파싱 ("추천 5" -> 5)
				String recoText = post.select("span.reco").text();
				int voteCount = 0;
				if (!recoText.isEmpty()) {
					try {
						// 숫자 이외의 문자 제거
						voteCount = Integer.parseInt(recoText.replaceAll("[^0-9]", ""));
					} catch (NumberFormatException e) {
						voteCount = 0;
					}
				}

				// 5. [추가] 댓글 수 파싱 (.com-btn 안의 .num)
				String commentText = post.select("a.com-btn .num").text();
				int commentCount = 0;
				if (!commentText.isEmpty()) {
					try {
						commentCount = Integer.parseInt(commentText);
					} catch (NumberFormatException e) {
						commentCount = 0;
					}
				}

				// 6. [추가] 조회수 파싱 ("조회 1,234" -> 1234)
				String viewText = post.select(".user_info .view").text();
				int viewCount = 0;
				if (!viewText.isEmpty()) {
					try {
						// "조회" 텍스트와 콤마(,) 제거 후 숫자만 남김
						viewCount = Integer.parseInt(viewText.replaceAll("[^0-9]", ""));
					} catch (NumberFormatException e) {
						viewCount = 0;
					}
				}

				// 8. 상세글 링크
				String postUrl = post.select("a.contentLink").attr("href");

				// DTO 생성
				postList.add(InvenPostDto.builder()
					.title(title)
					.author(author)
					.createdAt(createdAt)
					.postUrl(postUrl)
					.voteCount(voteCount)
					.commentCount(commentCount) // 추가됨
					.viewCount(viewCount)       // 추가됨
					.build());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return postList;
	}
}