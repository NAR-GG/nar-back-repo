package com.toy.nar.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
public class LoggingFilter extends OncePerRequestFilter {

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String requestURI = request.getRequestURI();
		return requestURI.startsWith("/actuator")
				|| requestURI.equals("/firebase-messaging-sw.js");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
		ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

		long startTime = System.currentTimeMillis();
		String requestUUID = UUID.randomUUID().toString().substring(0, 8);

		log.info("[START] Request ID: {}, URI: [{}]{}", requestUUID, request.getMethod(), request.getRequestURI());

		filterChain.doFilter(requestWrapper, responseWrapper);

		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		String requestBody = new String(requestWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
		String responseBody = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);

		log.info("[END] Request ID: {}, Status: {}, Duration: {}ms", requestUUID, responseWrapper.getStatus(), duration);
		if (!requestBody.isEmpty()) {
			log.debug("[REQUEST BODY] Request ID: {}, Body: {}", requestUUID, requestBody);
		}
		if (!responseBody.isEmpty()) {
			log.debug("[RESPONSE BODY] Request ID: {}, Body: {}", requestUUID, responseBody);
		}

		responseWrapper.copyBodyToResponse();
	}
}
