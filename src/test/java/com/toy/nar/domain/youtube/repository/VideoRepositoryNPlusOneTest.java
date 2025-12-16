package com.toy.nar.domain.youtube.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.toy.nar.common.config.QueryCountConfig;
import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VideoRepositoryNPlusOneTest {

	@Autowired
	private VideoRepository videoRepository;

	@Autowired
	private ChannelRepository channelRepository;

	@PersistenceContext
	private EntityManager entityManager;

	private SessionFactory sessionFactory;

	@BeforeEach
	void setUp() {
		sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
		sessionFactory.getStatistics().setStatisticsEnabled(true);
		sessionFactory.getStatistics().clear();

		// 데이터 준비: 채널 5개, 각 채널당 비디오 2개씩 총 10개 생성
		for (int i = 0; i < 5; i++) {
			Channel channel = Channel.builder()
				.youtubeChannelId("CH_" + i)
				.channelName("Channel " + i)
				.channelType(ChannelType.PRO_TEAMS)
				.build();
			channelRepository.save(channel);

			for (int j = 0; j < 2; j++) {
				Video video = Video.builder()
					.channel(channel)
					.youtubeVideoId("VID_" + i + "_" + j)
					.title("Video " + j)
					.build();
				videoRepository.save(video);
			}
		}

		entityManager.flush();
		entityManager.clear(); // 1차 캐시 비우기 (쿼리 발생 유도)
		sessionFactory.getStatistics().clear(); // 준비 과정 쿼리 통계 초기화
	}

	@Test
	@DisplayName("Specification 사용 시 EntityGraph를 적용하여 N+1 문제를 해결한다")
	void testFindAllWithSpecification_NPlusOneResolved() {
		// Given
		Specification<Video> spec = (root, query, cb) -> cb.conjunction(); // 조건 없음
		PageRequest pageable = PageRequest.of(0, 10);

		// When
		Page<Video> videos = videoRepository.findAll(spec, pageable);
		List<String> channelNames = videos.map(v -> v.getChannel().getChannelName()).getContent();

		// Then
		Statistics stats = sessionFactory.getStatistics();
		long queryCount = stats.getPrepareStatementCount();

		System.out.println("### 발생한 쿼리 개수: " + queryCount);
		
		// 예상: 1 (Video 조회 + Channel 패치조인) + 1 (Count 쿼리) = 2개
		// N+1이 발생했다면 6개 이상이었을 것임.
		assertThat(queryCount).as("N+1 문제가 해결되어 쿼리가 적게 발생해야 함").isLessThan(3); 
	}
}
