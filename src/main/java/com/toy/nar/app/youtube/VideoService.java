package com.toy.nar.app.youtube;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.youtube.dto.VideoListResponse;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;
import com.toy.nar.domain.youtube.repository.VideoRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoService {

	private final VideoRepository videoRepository;

	/**
	 * 비디오 목록 조회 (카테고리, 정렬, 기간 필터링 지원)
	 *
	 * @param category : "all", "pro", "shorts"
	 * @param sort     : "latest" (최신순), "popular" (조회수순)
	 * @param period   : "all", "week", "month"
	 * @param pageable : 페이징 정보
	 */
	public Page<VideoListResponse> getVideos(String category, String sort, String period, Pageable pageable) {
		// 1. Specification 생성 (필터링 조건)
		Specification<Video> spec = createSpecification(category, period);

		// 2. Sort 생성 (정렬 조건)
		Sort sorting = createSort(sort);

		// 3. Pageable 재구성 (정렬 적용)
		Pageable sortedPageable = PageRequest.of(
			pageable.getPageNumber(),
			pageable.getPageSize(),
			sorting
		);

		// 4. 조회 및 변환
		return videoRepository.findAll(spec, sortedPageable)
			.map(VideoListResponse::from);
	}

	private Specification<Video> createSpecification(String category, String period) {
		return (root, query, criteriaBuilder) -> {
			Predicate predicate = criteriaBuilder.conjunction();

			// 카테고리 필터
			if (category != null && !category.equalsIgnoreCase("all")) {
				if (category.equalsIgnoreCase("pro")) {
					predicate = criteriaBuilder.and(predicate,
						criteriaBuilder.equal(root.get("channel").get("channelType"), ChannelType.PRO_TEAMS));
				} else if (category.equalsIgnoreCase("shorts")) {
					predicate = criteriaBuilder.and(predicate,
						criteriaBuilder.equal(root.get("channel").get("channelType"), ChannelType.SHORTS));
				}
			}

			// 기간 필터
			if (period != null && !period.equalsIgnoreCase("all")) {
				LocalDateTime now = LocalDateTime.now();
				LocalDateTime startDate = null;

				if (period.equalsIgnoreCase("week")) {
					startDate = now.minusWeeks(1);
				} else if (period.equalsIgnoreCase("month")) {
					startDate = now.minusMonths(1);
				}

				if (startDate != null) {
					predicate = criteriaBuilder.and(predicate,
						criteriaBuilder.greaterThanOrEqualTo(root.get("publishedAt"), startDate));
				}
			}

			// N+1 방지를 위한 fetch join (EntityGraph 대신 코드 레벨 적용 고려 가능하지만,
			// Specification 사용 시 fetch join을 명시적으로 하기 까다로울 수 있음.
			// 일단 findAll(Spec, Pageable)은 EntityGraph 적용이 안되므로
			// BatchSize 설정이나 @EntityGraph를 Repository 메소드 오버라이드로 해결해야 함.
			// 여기선 기능 구현 우선. (VideoRepository의 findAll(Spec, Pageable)이 기본 제공이므로)
			
			return predicate;
		};
	}

	private Sort createSort(String sort) {
		if ("popular".equalsIgnoreCase(sort)) {
			// 인기순: 조회수 내림차순 -> (동률 시) 최신순
			return Sort.by(Sort.Direction.DESC, "viewCount", "publishedAt");
		}
		// 기본: 최신순
		return Sort.by(Sort.Direction.DESC, "publishedAt");
	}
}
