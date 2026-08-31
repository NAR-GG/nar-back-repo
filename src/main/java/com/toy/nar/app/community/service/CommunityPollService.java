package com.toy.nar.app.community.service;

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
 * 투표 — 글당 1개, 단일 선택, 투표 후 변경 불가.
 *
 * <p><b>변경 불가인 이유</b>: 결과가 투표 전 비공개(기본)인데 변경을 허용하면
 * "일단 찍고 결과 보고 갈아타기"로 숨김이 무력화된다. 재투표 시도는 409 로
 * 거절하고 현재 상태를 다시 그리게 한다.</p>
 *
 * <p>결과 분포(선택지별 표 수)는 투표했거나 공개 설정일 때만 내려간다 —
 * 미공개면 voteCount 를 null 로 비워 앱이 셈할 수 없게 서버에서 자른다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPollService {

	static final int MIN_OPTIONS = 2;
	static final int MAX_OPTIONS = 4;
	static final int MAX_QUESTION_LENGTH = 100;
	static final int MAX_OPTION_LENGTH = 50;

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
		pollRepository.createPoll(postId, question, trimmed);
	}

	/** 이 글의 투표(없으면 null). 상세 응답에 실린다. */
	public PollResponse findForViewer(long postId, Long viewerId) {
		return pollRepository.findByPostId(postId)
				.map(poll -> toResponse(poll, viewerId))
				.orElse(null);
	}

	/** 투표. 단일 선택·변경 불가 — 이미 투표했으면 409. */
	@Transactional
	public PollResponse vote(long postId, Long memberId, Long optionId) {
		CommunityPostService.requireLogin(memberId);
		if (optionId == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		PollRow poll = pollRepository.findByPostId(postId)
				.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_POLL_NOT_FOUND));
		// 선택지가 이 투표 소속인지 서버가 검증한다 — vote 테이블에 poll FK 를 안 건 대가.
		if (!pollRepository.optionBelongsToPoll(optionId, poll.id())) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		if (!pollRepository.insertVote(poll.id(), optionId, memberId)) {
			throw new CustomException(ErrorCode.COMMUNITY_ALREADY_VOTED);
		}
		pollRepository.applyVote(poll.id(), optionId);
		return toResponse(
				pollRepository.findByPostId(postId).orElseThrow(), memberId);
	}

	private PollResponse toResponse(PollRow poll, Long viewerId) {
		Long myOptionId = viewerId == null ? null : pollRepository.findMyOptionId(poll.id(), viewerId);
		boolean resultsVisible = myOptionId != null || !poll.hideResultsUntilVoted();
		List<PollOptionResponse> options = pollRepository.findOptions(poll.id()).stream()
				.map(option -> toOption(option, resultsVisible))
				.toList();
		return new PollResponse(poll.id(), poll.question(), poll.totalVotes(), resultsVisible,
				myOptionId, options);
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
