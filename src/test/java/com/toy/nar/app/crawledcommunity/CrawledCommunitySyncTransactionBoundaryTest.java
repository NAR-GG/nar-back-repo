package com.toy.nar.app.crawledcommunity;

import com.toy.nar.app.crawledcommunity.repository.CrawledCommunityPostRepository;
import com.toy.nar.app.crawledcommunity.repository.NewsPostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 크롤링이 트랜잭션 밖에서 일어나는지 지키는 회귀 테스트.
 *
 * 예전엔 syncAll 전체가 @Transactional 이라 외부 사이트 응답이 느리면
 * DB 커넥션과 테이블 락을 최대 수십 초 붙잡아 서비스 응답이 튀었다.
 */
class CommunitySyncTransactionBoundaryTest {

	private final OpggParserService opggParser = mock(OpggParserService.class);
	private final InvenParserService invenParser = mock(InvenParserService.class);
	private final NaverParserService naverParser = mock(NaverParserService.class);
	private final NaverNewsService naverNewsService = mock(NaverNewsService.class);
	private final CrawledCommunityPostRepository postRepository = mock(CrawledCommunityPostRepository.class);
	private final NewsPostRepository newsPostRepository = mock(NewsPostRepository.class);
	private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

	private final CrawledCommunityService service = new CrawledCommunityService(
			opggParser, invenParser, naverParser, naverNewsService,
			postRepository, newsPostRepository, transactionTemplate);

	@Test
	@DisplayName("크롤링은 트랜잭션이 열리기 전에 끝난다")
	void 크롤링은_트랜잭션_밖에서_수행된다() {
		when(opggParser.parseEsportsPosts("latest")).thenReturn(List.of());

		service.syncOpgg("latest");

		// 파싱이 먼저, 그 다음에야 트랜잭션이 열려야 한다.
		var order = inOrder(opggParser, transactionTemplate);
		order.verify(opggParser).parseEsportsPosts("latest");
		order.verify(transactionTemplate).executeWithoutResult(any());
		order.verifyNoMoreInteractions();
	}

	@Test
	@DisplayName("동기화 메서드에 @Transactional 이 다시 붙지 않았다")
	void 동기화_메서드는_선언적_트랜잭션을_쓰지_않는다() throws NoSuchMethodException {
		for (String methodName : List.of(
				"syncAll", "syncAllCommunities", "syncNaverNews", "syncOpgg", "syncInven", "syncNaver")) {
			assertThat(CrawledCommunityService.class.getMethod(methodName, String.class)
					.getAnnotation(Transactional.class))
					.as("CrawledCommunityService.%s 는 @Transactional 이면 크롤링이 트랜잭션에 갇힌다", methodName)
					.isNull();
		}

		assertThat(NaverNewsService.class.getMethod("syncNaverNews", String.class)
				.getAnnotation(Transactional.class))
				.as("NaverNewsService.syncNaverNews 는 @Transactional 이면 API 호출이 트랜잭션에 갇힌다")
				.isNull();
	}
}
