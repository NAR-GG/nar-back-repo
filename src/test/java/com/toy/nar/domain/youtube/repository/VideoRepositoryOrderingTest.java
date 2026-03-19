package com.toy.nar.domain.youtube.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;

@DataJpaTest
class VideoRepositoryOrderingTest {

	@Autowired
	private VideoRepository videoRepository;

	@Autowired
	private ChannelRepository channelRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@DisplayName("findByPublishedAtAfterOrderByPublishedAtAscIdAsc는 publishedAt과 id 순서로 정렬한다")
	void findByPublishedAtAfterOrderByPublishedAtAscIdAsc_returnsDeterministicOrdering() {
		LocalDateTime baseTime = LocalDateTime.of(2026, 3, 19, 12, 0);
		Channel channel = channelRepository.save(Channel.builder()
				.youtubeChannelId("ordering-channel")
				.channelName("Ordering Channel")
				.channelType(ChannelType.PRO_TEAMS)
				.build());

		// 저장 순서와 publishedAt 순서를 일부러 섞고, 같은 publishedAt도 만들어 tie-breaker를 검증한다.
		saveVideo(channel, "vid-b", "video-b", baseTime.minusHours(2));
		saveVideo(channel, "vid-a", "video-a", baseTime.minusHours(3));
		saveVideo(channel, "vid-c", "video-c", baseTime.minusHours(1));
		saveVideo(channel, "vid-d", "video-d", baseTime.minusHours(1));

		entityManager.flush();
		entityManager.clear();

		List<Video> result = videoRepository.findByPublishedAtAfterOrderByPublishedAtAscIdAsc(baseTime.minusHours(4));
		List<String> observedOrder = result.stream()
				.map(Video::getTitle)
				.toList();
		List<String> ascendingPublishedAtAndIdOrder = result.stream()
				.sorted(Comparator.comparing(Video::getPublishedAt)
						.thenComparing(Video::getId))
				.map(Video::getTitle)
				.toList();

		System.out.println("Observed order from findByPublishedAtAfterOrderByPublishedAtAscIdAsc:");
		result.forEach(video -> System.out.println(
				video.getId() + " | " + video.getTitle() + " | " + video.getPublishedAt()));

		assertThat(result).hasSize(4);
		assertThat(observedOrder)
				.as("조회 결과는 publishedAt ASC, id ASC 순서를 따라야 한다")
				.isEqualTo(ascendingPublishedAtAndIdOrder);
	}

	private void saveVideo(Channel channel, String youtubeVideoId, String title, LocalDateTime publishedAt) {
		videoRepository.save(Video.builder()
				.channel(channel)
				.youtubeVideoId(youtubeVideoId)
				.title(title)
				.videoUrl("https://www.youtube.com/watch?v=" + youtubeVideoId)
				.publishedAt(publishedAt)
				.build());
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EntityScan(basePackageClasses = { Channel.class, Video.class })
	@EnableJpaRepositories(basePackageClasses = { ChannelRepository.class, VideoRepository.class })
	static class TestJpaConfiguration {
	}
}
