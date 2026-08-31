package com.toy.nar.app.community.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.community.dto.CommunityDtos.PollCreateRequest;
import com.toy.nar.app.community.dto.CommunityDtos.PollOptionResponse;
import com.toy.nar.app.community.dto.CommunityDtos.PollResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.community.repository.CommunityPollRepository;
import com.toy.nar.domain.community.repository.CommunityPollRepository.PollOptionRow;
import com.toy.nar.domain.community.repository.CommunityPollRepository.PollRow;

import lombok.RequiredArgsConstructor;

/**
 * 투표 — 글당 1개, 투표 후 변경 불가. 옵션 3종:
 * 복수 선택(allowMultiple) · 결과 공개 방식(alwaysShowResults) · 마감(closesHours).
 *
 * <p><b>변경 불가인 이유</b>: 결과가 투표 전 비공개(기본)인데 변경을 허용하면
 * "일단 찍고 결과 보고 갈아타기"로 숨김이 무력화된다. 같은 선택지 재투표와
 * 단일 선택 투표 후 다른 선택지 투표 모두 409 다.</p>
 *
 * <p>결과 분포는 투표했거나 · 공개 설정이거나 · <b>마감됐을 때</b> 내려간다 —
 * 마감 후엔 투표할 수 없으니 숨길 이유가 없다. 미공개면 voteCount 를 null 로
 * 비워 앱이 셈할 수 없게 서버에서 자른다.</p>
 *
 * <p>totalVotes 응답은 참여자 수(사람 기준)다 — 복수 선택에서 표 수를 그대로
 * 쓰면 "3명 참여"가 "7명 참여"로 보인다. 퍼센트 분모도 참여자 수라 복수 선택은
 * 합이 100%를 넘을 수 있다(카카오톡과 같음).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPollService {

	static final int MIN_OPTIONS = 2;
	static final int MAX_OPTIONS = 4;
	static final int MAX_QUESTION_LENGTH = 100;
	static final int MAX_OPTION_LENGTH = 50;
	static final int MAX_CLOSES_HOURS = 168; // 7일

	private final CommunityPollRepository pollRepository;

	/** 글 작성 트랜잭션 안에서 호출 — 검증 실패는 글까지 함께 롤백된다. */
	@Transactional
	public void attachPoll(long postId, PollCreateRequest request) {
		String question = requireLength(request.question(), MAX_QUESTION_LENGTH);
		List<String> options = request.options() == null ? List.of() : request.options();
		if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		List<String> trimmed = options.stream()
				.map(label -> requireLength(label, MAX_OPTION_LENGTH))
				.toList();
		Integer closesHours = request.closesHours();
		if (closesHours != null && (closesHours < 1 || closesHours > MAX_CLOSES_HOURS)) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		pollRepository.createPoll(postId, question, trimmed,
				!Boolean.TRUE.equals(request.alwaysShowResults()),
				Boolean.TRUE.equals(request.allowMultiple()),
				closesHours == null ? null : LocalDateTime.now().plusHours(closesHours));
	}

	/** 이 글의 투표(없으면 null). 상세 응답에 실린다. */
	public PollResponse findForViewer(long postId, Long viewerId) {
		return pollRepository.findByPostId(postId)
				.map(poll -> toResponse(poll, viewerId))
				.orElse(null);
	}

	/** 투표. 마감이면 409, 이미 고른 선택지(또는 단일 선택에서 두 번째 표)도 409. */
	@Transactional
	public PollResponse vote(long postId, Long memberId, Long optionId) {
		CommunityPostService.requireLogin(memberId);
		if (optionId == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		PollRow poll = pollRepository.findByPostId(postId)
				.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_POLL_NOT_FOUND));
		if (isClosed(poll)) {
			throw new CustomException(ErrorCode.COMMUNITY_POLL_CLOSED);
		}
		// 선택지가 이 투표 소속인지 서버가 검증한다 — vote 테이블에 poll FK 를 안 건 대가.
		if (!pollRepository.optionBelongsToPoll(optionId, poll.id())) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		// uk 가 (poll, member, option) 으로 넓어져서 단일 선택의 "다른 선택지 두 번째 표"는
		// DB 가 못 막는다 — 서버가 막는다.
		if (!poll.allowMultiple() && !pollRepository.findMyOptionIds(poll.id(), memberId).isEmpty()) {
			throw new CustomException(ErrorCode.COMMUNITY_ALREADY_VOTED);
		}
		if (!pollRepository.insertVote(poll.id(), optionId, memberId)) {
			throw new CustomException(ErrorCode.COMMUNITY_ALREADY_VOTED);
		}
		pollRepository.applyVote(poll.id(), optionId);
		return toResponse(
				pollRepository.findByPostId(postId).orElseThrow(), memberId);
	}

	private PollResponse toResponse(PollRow poll, Long viewerId) {
		List<Long> myOptionIds = viewerId == null
				? List.of()
				: pollRepository.findMyOptionIds(poll.id(), viewerId);
		boolean closed = isClosed(poll);
		boolean resultsVisible = !myOptionIds.isEmpty() || !poll.hideResultsUntilVoted() || closed;
		List<PollOptionResponse> options = pollRepository.findOptions(poll.id()).stream()
				.map(option -> toOption(option, resultsVisible))
				.toList();
		return new PollResponse(poll.id(), poll.question(), pollRepository.countVoters(poll.id()),
				resultsVisible, poll.allowMultiple(), poll.closesAt(), closed, myOptionIds, options);
	}

	private static boolean isClosed(PollRow poll) {
		return poll.closesAt() != null && LocalDateTime.now().isAfter(poll.closesAt());
	}

	private static PollOptionResponse toOption(PollOptionRow option, boolean resultsVisible) {
		return new PollOptionResponse(option.id(), option.label(),
				resultsVisible ? option.voteCount() : null);
	}

	private static String requireLength(String value, int maxLength) {
		if (value == null || value.isBlank() || value.trim().length() > maxLength) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return value.trim();
	}
}
