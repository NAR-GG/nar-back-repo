package com.toy.nar.common;

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
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

		String requestURI = request.getRequestURI();
		if (requestURI.equals("/firebase-messaging-sw.js")) {
			filterChain.doFilter(request, response);
			return; // 로깅 로직을 실행하지 않고 바로 종료
		}
		ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
		ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

		long startTime = System.currentTimeMillis();
		String requestUUID = UUID.randomUUID().toString().substring(0, 8);

		log.info("[START] Request ID: {}, URI: [{}]{}", requestUUID, request.getMethod(), request.getRequestURI());

		filterChain.doFilter(requestWrapper, responseWrapper);

		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 컨트롤러 처리 후, 캐싱된 요청/응답 본문 로깅
		String requestBody = new String(requestWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
		String responseBody = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);

		log.info("[END] Request ID: {}, Status: {}, Duration: {}ms", requestUUID, responseWrapper.getStatus(), duration);
		if (!requestBody.isEmpty()) {
			log.debug("[REQUEST BODY] Request ID: {}, Body: {}", requestUUID, requestBody);
		}
		if (!responseBody.isEmpty()) {
			log.debug("[RESPONSE BODY] Request ID: {}, Body: {}", requestUUID, responseBody);
		}

		// 4. 캐싱된 응답을 실제 응답 객체에 복사하여 클라이언트에게 전달
		responseWrapper.copyBodyToResponse();
	}
}