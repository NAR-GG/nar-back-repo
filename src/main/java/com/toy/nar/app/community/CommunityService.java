package com.toy.nar.app.community;

import com.toy.nar.app.community.dto.InvenPostDto;
import com.toy.nar.app.community.dto.NaverPostDto;
import com.toy.nar.app.community.dto.OpggPostDto;
import com.toy.nar.app.community.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityService {

	private final OpggParserService opggParserService;
	private final InvenParserService invenParserService;
	private final NaverParserService naverParserService;
	private final NaverNewsService naverNewsService;
	private final CommunityPostRepository communityPostRepository;
	private final EsportsNewsRepository esportsNewsRepository;

	/**
	 * 모든 커뮤니티 및 뉴스를 동기화합니다.
	 */
	@Transactional
	public void syncAll(String sortType) {
		syncAllCommunities(sortType);
		syncNaverNews(sortType);
	}

	@Transactional
	public void syncAllCommunities(String sortType) {
		syncOpgg(sortType);
		syncInven(sortType);
		syncNaver(sortType);
	}

	@Transactional
	public void syncNaverNews(String sortType) {
		naverNewsService.syncNaverNews(sortType);
	}

	@Transactional
	public void syncOpgg(String sortType) {
		List<OpggPostDto> posts = opggParserService.parseEsportsPosts(sortType);
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
		log.info("Synced {} OP.GG posts ({})", posts.size(), sortType);
	}

	@Transactional
	public void syncInven(String sortType) {
		List<InvenPostDto> posts = invenParserService.parseInvenPosts(sortType);
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
		log.info("Synced {} Inven posts ({})", posts.size(), sortType);
	}

	@Transactional
	public void syncNaver(String sortType) {
		List<NaverPostDto> posts = naverParserService.parseNaverPosts(sortType);
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
		log.info("Synced {} Naver posts ({})", posts.size(), sortType);
	}

	private void saveOrUpdate(CommunityType type, String title, String author, String url, String createdAtStr, 
							  int viewCount, int voteCount, int commentCount) {
		try {
			LocalDateTime createdAt = parseDateTime(createdAtStr);
			
			Optional<CommunityPost> existingPost = communityPostRepository.findByPostUrl(url);

			if (existingPost.isPresent()) {
				existingPost.get().updateStatistics(viewCount, voteCount, commentCount);
			} else {
				CommunityPost newPost = CommunityPost.builder()
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
	public List<CommunityPost> getTop5Posts(String sortType) {
		org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 5);
		
		if ("popular".equalsIgnoreCase(sortType)) {
			return communityPostRepository.findAllByOrderByVoteCountDesc(pageable);
		} else {
			return communityPostRepository.findAllByOrderByCreatedAtDesc(pageable);
		}
	}

	@Transactional(readOnly = true)
	public List<EsportsNews> getTop5News() {
		return esportsNewsRepository.findAllByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 5));
	}

	@Transactional
	public void deletePostsOlderThan(int days) {
		LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
		communityPostRepository.deleteByCreatedAtBefore(cutoffDate);
		log.info("Deleted community posts older than {} days", days);
	}
}