package com.toy.nar.app.lolesports.live;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스프링이 이 빈을 실제로 생성할 수 있는지 지키는 회귀 테스트.
 *
 * 생성자가 둘(운영용 @Value + 테스트용 Clock 주입)인데 @Autowired 지정이 없으면 스프링은
 * 기본 생성자를 찾다 기동에 실패한다. 단위 테스트는 직접 생성이라 이걸 못 잡았고, 풀 컨텍스트
 * 테스트는 로컬 전용 게이트라 CI 도 못 잡아 2026-07-30 배포가 헬스체크에서 반려됐다.
 * ApplicationContextRunner 는 이 빈 하나만 컨텍스트로 띄우므로 CI 에서 항상 돈다.
 */
class LiveFrameStallTrackerBeanCreationTest {

	@Test
	@DisplayName("스프링 컨텍스트가 빈을 생성할 수 있다")
	void 스프링이_빈을_생성한다() {
		new ApplicationContextRunner()
				.withUserConfiguration(LiveFrameStallTracker.class)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(LiveFrameStallTracker.class);
				});
	}
}
