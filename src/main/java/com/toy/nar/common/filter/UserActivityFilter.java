package com.toy.nar.common.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 접속 사용자 계측. Grafana 의 "동시접속 사용자"·"금일 사용자"가 이 로그를 센다.
 *
 * <p>집계를 앱에 두지 않는다. 요청마다 식별자 한 줄만 뱉고 고유 수는 Loki 가 쿼리
 * 시점에 계산한다. 파드가 죽어도 값이 리셋되지 않고, 웹을 여러 대로 늘려도 같은
 * 사용자가 중복으로 세지지 않는다 — 인메모리 Set 으로는 둘 다 불가능했다.
 * (고유 사용자 수는 집합 카디널리티라 파드별 값을 더할 수 없다. Counter 와 달리
 * Prometheus 가 보정해줄 수 없는 이유다.)
 *
 * <p>식별 기준은 로그인 회원 우선, 없으면 클라이언트 IP 다. 모바일 앱은 대부분 로그인
 * 상태라 회원으로 잡히고, 웹·비로그인 조회는 IP 로 잡힌다.
 *
 * <p>순서가 중요하다. JwtAuthenticationFilter 뒤에 서야 SecurityContext 에 memberId 가
 * 들어와 있다. 앞에 서면 전부 IP 로 세어 회원 식별이 통째로 사라진다.
 */
@Order(Integer.MAX_VALUE)
@Component
public class UserActivityFilter extends OncePerRequestFilter {

	/**
	 * 대시보드가 이 문자열로 로그를 고른다(LogQL {@code |= "user_activity"}).
	 * {@code uid=} 뒤 토큰도 정규식으로 뽑아 쓰므로 형식을 바꾸면 패널이 빈다.
	 */
	private static final String ACTIVITY_FORMAT = "user_activity uid={}";

	/**
	 * 전용 로거. 요청 1건당 1줄이라 볼륨이 크다. 로그가 부담되면 재배포 없이
	 * {@code logging.level.user-activity=OFF} 로 끈다 — 대신 지표도 같이 멈춘다.
	 */
	private static final Logger ACTIVITY_LOG = LoggerFactory.getLogger("user-activity");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {

		ACTIVITY_LOG.info(ACTIVITY_FORMAT, identify(request));
		filterChain.doFilter(request, response);
	}

	/** 헬스체크·메트릭은 사용자가 아니다. 세면 프로브가 상시 접속자로 잡힌다. */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return uri.startsWith("/actuator");
	}

	String identify(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long memberId) {
			return "m:" + memberId;
		}
		return "i:" + clientIp(request);
	}

	/**
	 * getRemoteAddr() 는 여기서 쓸모없다 — cloudflared·Traefik 을 거쳐 오므로 전부
	 * 내부 IP 한두 개로 뭉쳐 동시접속이 1~2 로 보인다. Cloudflare 가 넣어주는
	 * CF-Connecting-IP 가 진짜 클라이언트고, Traefik 이 그 헤더를 살려서 넘긴다
	 * (traefik-app.yaml 의 forwardedHeaders.trustedIPs).
	 */
	private String clientIp(HttpServletRequest request) {
		String cf = request.getHeader("CF-Connecting-IP");
		if (cf != null && !cf.isBlank()) {
			return cf;
		}
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			int comma = xff.indexOf(',');
			return (comma > 0 ? xff.substring(0, comma) : xff).trim();
		}
		return request.getRemoteAddr();
	}
}
