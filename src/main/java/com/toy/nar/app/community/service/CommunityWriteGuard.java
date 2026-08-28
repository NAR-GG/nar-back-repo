package com.toy.nar.app.community.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CommunityWriteBlockedException;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.community.repository.CommunityCommentRepository;
import com.toy.nar.domain.community.repository.CommunityPostRepository;
import com.toy.nar.domain.member.entity.Member;

import lombok.RequiredArgsConstructor;

/**
 * 커뮤니티 쓰기 공통 검사. 앱이 버튼을 가리는 건 UX 고, 판정은 여기가 최종이다 —
 * API 직접 호출로 남의 팀 게시판에 쓰는 경로를 서버가 막는다.
 *
 * <p>시간 조건 거부(쿨다운·간격)는 {@link CommunityWriteBlockedException} 으로 던져
 * 남은 초가 Retry-After 헤더로 내려간다.</p>
 */
@Component
@RequiredArgsConstructor
public class CommunityWriteGuard {

	private final CommunityPostRepository postRepository;
	private final CommunityCommentRepository commentRepository;

	@Value("${community.post-interval-seconds:60}")
	private long postIntervalSeconds;

	@Value("${community.comment-interval-seconds:10}")
	private long commentIntervalSeconds;

	@Value("${community.team-change-cooldown-days:30}")
	private long teamChangeCooldownDays;

	/** 게시판 쓰기 자격 판정 결과. reason 은 NOT_FAN / COOLDOWN / null(쓰기 가능). */
	public record BoardWritability(boolean canWrite, String reason, LocalDateTime writableFrom) {

		static final BoardWritability WRITABLE = new BoardWritability(true, null, null);
	}

	/**
	 * 게시판 쓰기 자격 판정. 전체 게시판(boardTeamId == null)은 항상 가능,
	 * 팀 게시판은 응원팀 일치 + 팀 변경 30일 쿨다운(D-1)을 본다.
	 * 쓰기 경로(예외)와 목록의 잠금 바(boardViewer)가 같은 판정을 쓴다.
	 */
	public BoardWritability evaluateBoardWritability(Member member, Long boardTeamId) {
		if (boardTeamId == null) {
			return BoardWritability.WRITABLE;
		}
		Long favoriteTeamId = member.getFavoriteTeam() == null ? null : member.getFavoriteTeam().getId();
		if (!boardTeamId.equals(favoriteTeamId)) {
			return new BoardWritability(false, "NOT_FAN", null);
		}
		LocalDateTime changedAt = member.getFavoriteTeamChangedAt();
		if (changedAt != null) {
			LocalDateTime writableFrom = changedAt.plusDays(teamChangeCooldownDays);
			if (remainingSeconds(writableFrom) > 0) {
				return new BoardWritability(false, "COOLDOWN", writableFrom);
			}
		}
		return BoardWritability.WRITABLE;
	}

	/** 쓰기 경로용 — 판정 결과를 예외로 바꾼다. 글·댓글 공통. */
	public void checkBoardWritable(Member member, Long boardTeamId) {
		BoardWritability result = evaluateBoardWritability(member, boardTeamId);
		if (result.canWrite()) {
			return;
		}
		if ("COOLDOWN".equals(result.reason())) {
			throw new CommunityWriteBlockedException(ErrorCode.COMMUNITY_TEAM_COOLDOWN,
					remainingSeconds(result.writableFrom()));
		}
		throw new CustomException(ErrorCode.COMMUNITY_BOARD_FORBIDDEN);
	}

	/** 글 작성 간격(D-9). status 무관 최신 1행 — 지웠다 다시 올리는 우회도 잡는다. */
	public void checkPostInterval(long memberId) {
		checkInterval(postRepository.findLastCreatedAt(memberId).orElse(null), postIntervalSeconds);
	}

	public void checkCommentInterval(long memberId) {
		checkInterval(commentRepository.findLastCreatedAt(memberId).orElse(null), commentIntervalSeconds);
	}

	private void checkInterval(LocalDateTime lastCreatedAt, long intervalSeconds) {
		if (lastCreatedAt == null) {
			return;
		}
		long remaining = remainingSeconds(lastCreatedAt.plusSeconds(intervalSeconds));
		if (remaining > 0) {
			throw new CommunityWriteBlockedException(ErrorCode.COMMUNITY_WRITE_INTERVAL, remaining);
		}
	}

	private static long remainingSeconds(LocalDateTime until) {
		return Duration.between(LocalDateTime.now(), until).getSeconds();
	}
}
