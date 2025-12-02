package com.toy.nar.app.youtube;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.youtube.dto.VideoListResponse;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;
import com.toy.nar.domain.youtube.repository.VideoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoService {

	private final VideoRepository videoRepository;

	/**
	 * 카테고리별 비디오 페이징 조회
	 * * @param category : "all", "pro", "shorts" (대소문자 무관)
	 * @param pageable : 페이징 정보
	 */
	public Page<VideoListResponse> getVideosByCategory(String category, Pageable pageable) {
		// 1. 최신순 정렬 강제 적용 (클라이언트가 sort를 안 보내도 기본적으로 최신순)
		Pageable sortedPageable = PageRequest.of(
			pageable.getPageNumber(),
			pageable.getPageSize(),
			Sort.by(Sort.Direction.DESC, "publishedAt")
		);

		Page<Video> videoPage;

		// 2. 카테고리 분기 처리
		if (category == null || category.equalsIgnoreCase("all")) {
			videoPage = videoRepository.findAll(sortedPageable);
		} else if (category.equalsIgnoreCase("pro")) {
			videoPage = videoRepository.findByChannel_ChannelType(ChannelType.PRO_TEAMS, sortedPageable);
		} else if (category.equalsIgnoreCase("shorts")) {
			videoPage = videoRepository.findByChannel_ChannelType(ChannelType.SHORTS, sortedPageable);
		} else {
			// 잘못된 카테고리면 빈 페이지 혹은 전체 반환 (여기선 전체 반환으로 처리)
			videoPage = videoRepository.findAll(sortedPageable);
		}

		// 3. DTO 변환
		return videoPage.map(VideoListResponse::from);
	}
}
