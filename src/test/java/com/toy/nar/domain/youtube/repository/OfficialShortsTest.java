package com.toy.nar.domain.youtube.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

import com.toy.nar.app.youtube.VideoService;
import com.toy.nar.app.youtube.YoutubeService;
import com.toy.nar.app.youtube.YoutubeSyncService;
import com.toy.nar.app.youtube.dto.VideoListResponse;
import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;

// 공식 쇼츠 = is_short 가 true 이고 유튜버 채널(SHORTS)이 아닌 영상. 판별 스윕과 함께 H2 에서 잠근다.
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
// 같은 패키지에 @SpringBootConfiguration 을 또 두면 VideoRepositoryNPlusOneTest 의 탐색이 깨져 기존 설정을 재사용한다.
@ContextConfiguration(classes = VideoRepositoryOrderingTest.TestJpaConfiguration.class)
class OfficialShortsTest {

	@Autowired
	private VideoRepository videoRepository;

	@Autowired
	private ChannelRepository channelRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@DisplayName("category=shorts 는 팀·LCK 채널의 쇼츠만 주고, 유튜버 채널은 all 에서도 뺀다")
	void shortsCategory_returnsOnlyOfficialShorts() {
		Channel team = channel("UC_team", ChannelType.PRO_TEAMS);
		Channel youtuber = channel("UC_youtuber", ChannelType.SHORTS);

		video(team, "official-short", true);
		video(team, "long-form", false);
		video(team, "unclassified", null);
		video(youtuber, "youtuber-short", true);
		flushAndClear();

		List<String> ids = new VideoService(videoRepository)
				.getVideos("shorts", "latest", "all", PageRequest.of(0, 20))
				.map(VideoListResponse::youtubeVideoId)
				.getContent();

		assertThat(ids).containsExactly("official-short");

		List<String> all = new VideoService(videoRepository)
				.getVideos("all", "latest", "all", PageRequest.of(0, 20))
				.map(VideoListResponse::youtubeVideoId)
				.getContent();

		assertThat(all).as("유튜버 채널은 all 에서도 빠진다")
				.containsExactlyInAnyOrder("official-short", "long-form", "unclassified");
	}

	@Test
	@DisplayName("판별 스윕은 shorts 로 판정되면 링크를 /shorts/ 로 바꾸고, 판별 불가에서 멈춘다")
	void classifyPendingShorts_updatesUrlAndStopsOnUnknown() {
		Channel team = channel("UC_team", ChannelType.PRO_TEAMS);
		Channel youtuber = channel("UC_youtuber", ChannelType.SHORTS);
		LocalDateTime t = LocalDateTime.of(2026, 9, 30, 12, 0);

		video(team, "a-short", null, t.minusHours(1));
		video(team, "b-long", null, t.minusHours(2));
		video(team, "c-blocked", null, t.minusHours(3));
		video(team, "d-never-reached", null, t.minusHours(4));
		video(youtuber, "e-skipped", null, t.minusHours(5));
		flushAndClear();

		YoutubeService youtubeService = mock(YoutubeService.class);
		when(youtubeService.isShort(any())).thenAnswer(inv -> switch ((String) inv.getArgument(0)) {
			case "a-short" -> true;
			case "b-long" -> false;
			default -> null; // c-blocked 에서 멈춰야 한다
		});
		YoutubeSyncService sweep = new YoutubeSyncService(youtubeService, channelRepository, videoRepository,
				null, null, null, null);

		assertThat(sweep.classifyPendingShorts(10)).isEqualTo(2);
		flushAndClear();

		assertThat(videoRepository.findByYoutubeVideoId("a-short").orElseThrow())
				.extracting(Video::getIsShort, Video::getVideoUrl)
				.containsExactly(true, "https://www.youtube.com/shorts/a-short");
		assertThat(videoRepository.findByYoutubeVideoId("b-long").orElseThrow())
				.extracting(Video::getIsShort, Video::getVideoUrl)
				.containsExactly(false, "https://www.youtube.com/watch?v=b-long");
		assertThat(videoRepository.findByYoutubeVideoId("c-blocked").orElseThrow().getIsShort()).isNull();
		assertThat(videoRepository.findByYoutubeVideoId("d-never-reached").orElseThrow().getIsShort()).isNull();
		assertThat(videoRepository.findByYoutubeVideoId("e-skipped").orElseThrow().getIsShort())
				.as("유튜버 채널은 판별 대기열에 들어오지 않는다").isNull();
		assertThat(videoRepository.findUnclassified(ChannelType.SHORTS, PageRequest.of(0, 10))).hasSize(2);
	}

	private Channel channel(String youtubeChannelId, ChannelType type) {
		return channelRepository.save(Channel.builder()
				.youtubeChannelId(youtubeChannelId)
				.channelName(youtubeChannelId)
				.channelType(type)
				.build());
	}

	private void video(Channel channel, String youtubeVideoId, Boolean isShort) {
		video(channel, youtubeVideoId, isShort, LocalDateTime.of(2026, 9, 30, 12, 0));
	}

	private void video(Channel channel, String youtubeVideoId, Boolean isShort, LocalDateTime publishedAt) {
		videoRepository.save(Video.builder()
				.channel(channel)
				.youtubeVideoId(youtubeVideoId)
				.title(youtubeVideoId)
				.videoUrl("https://www.youtube.com/watch?v=" + youtubeVideoId)
				.publishedAt(publishedAt)
				.isShort(isShort)
				.build());
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
