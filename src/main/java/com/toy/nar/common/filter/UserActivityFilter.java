package com.toy.nar.common.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.toy.nar.app.monitor.UserActivityService;

@Component
@RequiredArgsConstructor
public class UserActivityFilter extends OncePerRequestFilter {

	private final UserActivityService userActivityService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {

		String userIdentifier = request.getRemoteAddr();
		userActivityService.recordUserActivity(userIdentifier);

		filterChain.doFilter(request, response);
	}
}
