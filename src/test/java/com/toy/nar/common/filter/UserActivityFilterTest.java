package com.toy.nar.common.filter;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 필터가 뱉는 로그 한 줄이 곧 대시보드의 데이터 원천이다. 그래서 여기서 검증하는 건
 * "필터가 도는가"가 아니라 <b>대시보드가 파싱할 수 있는 줄이 나오는가</b>다.
 *
 * <p>{@link #LOGQL_UID} 는 Grafana 패널이 쓰는 정규식과 같은 것이다. 로그 형식을 바꾸면
 * 여기서 먼저 깨진다 — 패널이 조용히 빈 채로 배포되는 걸 막는 유일한 장치다.
 */
class UserActivityFilterTest {

	/** 대시보드 쿼리의 {@code | regexp "uid=(?P<uid>\S+)"} 와 동일하다. */
	private static final Pattern LOGQL_UID = Pattern.compile("uid=(\\S+)");

	/** 대시보드 쿼리의 {@code |= "user_activity"} 라인 필터와 동일하다. */
	private static final String LOGQL_LINE_FILTER = "user_activity";

	private final UserActivityFilter filter = new UserActivityFilter();
	private final Logger activityLogger = (Logger) LoggerFactory.getLogger("user-activity");
	private final ListAppender<ILoggingEvent> captured = new ListAppender<>();

	@BeforeEach
	void attachAppender() {
		captured.start();
		activityLogger.addAppender(captured);
		activityLogger.setLevel(Level.INFO);
	}

	@AfterEach
	void detachAppender() {
		activityLogger.detachAppender(captured);
		SecurityContextHolder.clearContext();
	}

	@Test
	void 로그인_회원은_memberId_로_식별된다() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(12L, null, List.of()));

		assertThat(uidFrom(request("/api/v3/schedule"))).isEqualTo("m:12");
	}

	@Test
	void 비로그인은_CF_Connecting_IP_로_식별된다() throws Exception {
		MockHttpServletRequest request = request("/api/v3/schedule");
		request.addHeader("CF-Connecting-IP", "203.0.113.7");
		// 프록시 체인이 남긴 값이 있어도 Cloudflare 헤더가 이긴다
		request.addHeader("X-Forwarded-For", "10.42.0.1");

		assertThat(uidFrom(request)).isEqualTo("i:203.0.113.7");
	}

	@Test
	void CF_헤더가_없으면_XFF_의_첫_IP_를_쓴다() throws Exception {
		MockHttpServletRequest request = request("/api/v3/schedule");
		request.addHeader("X-Forwarded-For", "203.0.113.9, 10.42.0.1");

		assertThat(uidFrom(request)).isEqualTo("i:203.0.113.9");
	}

	@Test
	void actuator_는_세지_않는다() throws Exception {
		// 프로브가 30초마다 때리므로 세면 상시 접속자 1~2 명이 깔린다
		filter.doFilter(request("/actuator/health"), new MockHttpServletResponse(), new MockFilterChain());

		assertThat(captured.list).isEmpty();
	}

	private MockHttpServletRequest request(String uri) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
		request.setRemoteAddr("10.42.0.1");
		return request;
	}

	/** 대시보드가 하는 일과 똑같이 — 라인 필터로 고르고 정규식으로 uid 를 뽑는다. */
	private String uidFrom(MockHttpServletRequest request) throws Exception {
		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(captured.list).hasSize(1);
		String line = captured.list.get(0).getFormattedMessage();
		assertThat(line).contains(LOGQL_LINE_FILTER);

		Matcher matcher = LOGQL_UID.matcher(line);
		assertThat(matcher.find()).as("대시보드 정규식이 uid 를 못 뽑는다: %s", line).isTrue();
		return matcher.group(1);
	}
}
