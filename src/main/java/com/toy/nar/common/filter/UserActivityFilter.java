package com.toy.nar.common.filter;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.toy.nar.app.monitor.UserActivityService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 접속 사용자 집계용 필터. Grafana 의 "동시접속 사용자"·"금일 사용자"가 이 값을 본다.
 *
 * 식별 기준은 로그인 회원 우선, 없으면 클라이언트 IP 다. 모바일 앱은 대부분 로그인
 * 상태라 회원으로 잡히고, 웹·비로그인 조회는 IP 로 잡힌다.
 *
 * <p>순서가 중요하다. JwtAuthenticationFilter 뒤에 서야 SecurityContext 에 memberId 가
 * 들어와 있다. 앞에 서면 전부 IP 로 세어 회원 식별이 통째로 사라진다.
 */
@Order(Integer.MAX_VALUE)
@Component
@RequiredArgsConstructor
public class UserActivityFilter extends OncePerRequestFilter {

	private final UserActivityService userActivityService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {

		userActivityService.recordUserActivity(identify(request));
		filterChain.doFilter(request, response);
	}

	/** 헬스체크·메트릭은 사용자가 아니다. 세면 프로브가 상시 접속자로 잡힌다. */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return uri.startsWith("/actuator");
	}

	private String identify(HttpServletRequest request) {
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
