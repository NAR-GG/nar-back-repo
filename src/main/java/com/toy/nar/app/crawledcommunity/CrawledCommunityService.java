package com.toy.nar.app.crawledcommunity;

import com.toy.nar.app.crawledcommunity.dto.InvenPostDto;
import com.toy.nar.app.crawledcommunity.dto.NaverPostDto;
import com.toy.nar.app.crawledcommunity.dto.OpggPostDto;
import com.toy.nar.app.crawledcommunity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawledCommunityService {

	private final OpggParserService opggParserService;
	private final InvenParserService invenParserService;
	private final NaverParserService naverParserService;
	private final NaverNewsService naverNewsService;
	private final CrawledCommunityPostRepository communityPostRepository;
	private final NewsPostRepository newsPostRepository;
	private final TransactionTemplate transactionTemplate;

	/**
	 * 모든 커뮤니티 및 뉴스를 동기화합니다.
	 *
	 * 크롤링(Jsoup)은 트랜잭션 밖에서 수행하고, DB 쓰기 구간만 짧게 트랜잭션으로 감싼다.
	 * 예전엔 이 메서드 전체가 @Transactional 이라 외부 사이트 응답이 느리면
	 * 커넥션 1개와 community/news 테이블 락을 최대 수십 초씩 붙잡아
	 * 10분 주기로 서비스 전체 응답이 튀었다.
	 */
	public void syncAll(String sortType) {
		syncAllCommunities(sortType);
		syncNaverNews(sortType);
	}

	public void syncAllCommunities(String sortType) {
		syncOpgg(sortType);
		syncInven(sortType);
		syncNaver(sortType);
	}

	public void syncNaverNews(String sortType) {
		naverNewsService.syncNaverNews(sortType);
	}

	public void syncOpgg(String sortType) {
		List<OpggPostDto> posts = opggParserService.parseEsportsPosts(sortType);
		transactionTemplate.executeWithoutResult(status -> {
			for (OpggPostDto dto : posts) {
				saveOrUpdate(
					CommunityType.OPGG,
					dto.getTitle(),
					dto.getAuthor(),
					dto.getPostUrl(),
					dto.getCreatedAt(),
					dto.getViewCount(),
					dto.getVoteCount(),
					dto.getCommentCount()
				);
			}
		});
		log.info("Synced {} OP.GG posts ({})", posts.size(), sortType);
	}

	public void syncInven(String sortType) {
		List<InvenPostDto> posts = invenParserService.parseInvenPosts(sortType);
		transactionTemplate.executeWithoutResult(status -> {
			for (InvenPostDto dto : posts) {
				saveOrUpdate(
					CommunityType.INVEN,
					dto.getTitle(),
					dto.getAuthor(),
					dto.getPostUrl(),
					dto.getCreatedAt(),
					dto.getViewCount(),
					dto.getVoteCount(),
					dto.getCommentCount()
				);
			}
		});
		log.info("Synced {} Inven posts ({})", posts.size(), sortType);
	}

	public void syncNaver(String sortType) {
		List<NaverPostDto> posts = naverParserService.parseNaverPosts(sortType);
		transactionTemplate.executeWithoutResult(status -> {
			for (NaverPostDto dto : posts) {
				saveOrUpdate(
					CommunityType.NAVER,
					dto.getTitle(),
					dto.getAuthor(),
					dto.getPostUrl(),
					dto.getCreatedAt(),
					dto.getViewCount(),
					dto.getVoteCount(),
					dto.getCommentCount()
				);
			}
		});
		log.info("Synced {} Naver posts ({})", posts.size(), sortType);
	}

	private void saveOrUpdate(CommunityType type, String title, String author, String url, String createdAtStr, 
							  int viewCount, int voteCount, int commentCount) {
		try {
			LocalDateTime createdAt = parseDateTime(createdAtStr);
			
			Optional<CrawledCommunityPost> existingPost = communityPostRepository.findByPostUrl(url);

			if (existingPost.isPresent()) {
				existingPost.get().updateStatistics(viewCount, voteCount, commentCount);
			} else {
				CrawledCommunityPost newPost = CrawledCommunityPost.builder()
					.communityType(type)
					.title(title)
					.author(author)
					.postUrl(url)
					.createdAt(createdAt)
					.viewCount(viewCount)
					.voteCount(voteCount)
					.commentCount(commentCount)
					.lastUpdated(LocalDateTime.now())
					.build();
				communityPostRepository.save(newPost);
			}
		} catch (Exception e) {
			log.error("Failed to save post: {} ({})", title, url, e);
		}
	}

	private LocalDateTime parseDateTime(String dateTimeStr) {
		if (dateTimeStr == null) return LocalDateTime.now();
		try {
			if (dateTimeStr.contains("T")) {
				return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
			} else {
				return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			}
		} catch (Exception e) {
			log.warn("Date parsing failed for '{}', using current time.", dateTimeStr);
			return LocalDateTime.now();
		}
	}

	@Transactional(readOnly = true)
	public List<CrawledCommunityPost> getTop5Posts(String sortType) {
		org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 5);
		
		if ("popular".equalsIgnoreCase(sortType)) {
			return communityPostRepository.findAllByOrderByVoteCountDesc(pageable);
		} else {
			return communityPostRepository.findAllByOrderByCreatedAtDesc(pageable);
		}
	}

	@Transactional(readOnly = true)
	public List<NewsPost> getTop5News() {
		return newsPostRepository.findAllByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 5));
	}

	@Transactional
	public void deletePostsOlderThan(int days) {
		LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
		communityPostRepository.deleteByCreatedAtBefore(cutoffDate);
		newsPostRepository.deleteByCreatedAtBefore(cutoffDate);
		log.info("Deleted community posts and news older than {} days (before {})", days, cutoffDate);
	}
}