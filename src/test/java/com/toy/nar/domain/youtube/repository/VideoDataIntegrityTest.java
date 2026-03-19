package com.toy.nar.domain.youtube.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.Video;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VideoDataIntegrityTest {

	@Autowired
	private VideoRepository videoRepository;

	@Autowired
	private ChannelRepository channelRepository;

	@Test
	@DisplayName("롤뻔뻔 채널의 최근 1달 전 데이터가 정상적으로 수집되었는지 확인한다")
	void checkLolPpeonPpeonDataIntegrity() {
		// 1. "롤뻔뻔" 채널 찾기
		// (정확한 채널명을 모를 수 있으니 부분 일치 검색 또는 로직으로 찾기)
		Channel channel = channelRepository.findAll().stream()
			.filter(c -> "롤뻔뻔".equals(c.getChannelName()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("테스트 실패: '롤뻔뻔' 채널이 DB에 없습니다."));

		// 2. 검증 기간 설정 (오늘 기준 1달 전 ~ 1달 전 + 7일)
		// 오늘이 2025-12-16이라면, 1달 전은 11-16
		LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		LocalDateTime oneMonthAgo = now.minusDays(30).withHour(0).withMinute(0).withSecond(0);
		LocalDateTime oneMonthAgoPlus7Days = oneMonthAgo.plusDays(7).withHour(23).withMinute(59).withSecond(59);

		System.out.println("### 검증 기간: " + oneMonthAgo + " ~ " + oneMonthAgoPlus7Days);

		// 3. 해당 기간의 영상 조회
		// (VideoRepository에 기간 조회 메서드가 없으므로 findAll 후 필터링하거나 쿼리 메서드 사용)
		List<Video> videos = videoRepository.findByPublishedAtAfterOrderByPublishedAtAscIdAsc(oneMonthAgo).stream()
			.filter(v -> v.getChannel().equals(channel))
			.filter(v -> v.getPublishedAt().isBefore(oneMonthAgoPlus7Days))
			.toList();

		System.out.println("### 조회된 영상 개수: " + videos.size());
		videos.forEach(v -> System.out.println(" - [" + v.getPublishedAt() + "] " + v.getTitle()));

		// 4. 검증: 쇼츠 채널은 매일 1개 이상 올리므로, 7일간 최소 5개 이상은 있어야 함
		assertThat(videos).isNotEmpty();
		assertThat(videos.size()).as("해당 기간 동안 영상이 너무 적습니다. 누락 가능성 있음.").isGreaterThanOrEqualTo(5);
	}
}
