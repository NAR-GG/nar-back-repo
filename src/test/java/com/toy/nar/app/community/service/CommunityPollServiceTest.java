package com.toy.nar.app.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.toy.nar.app.community.dto.CommunityDtos.PollCreateRequest;
import com.toy.nar.app.community.dto.CommunityDtos.PollResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.community.repository.CommunityPollRepository;
import com.toy.nar.domain.community.repository.CommunityPollRepository.PollOptionRow;
import com.toy.nar.domain.community.repository.CommunityPollRepository.PollRow;

class CommunityPollServiceTest {

	private final CommunityPollRepository repository = mock(CommunityPollRepository.class);
	private final CommunityPollService service = new CommunityPollService(repository);

	private static final PollRow POLL = new PollRow(5L, 1L, "우승팀은?", true, false, null, 3);

	private void stubPoll(PollRow poll) {
		lenient().when(repository.findByPostId(1L)).thenReturn(Optional.of(poll));
		lenient().when(repository.findOptions(5L)).thenReturn(List.of(
				new PollOptionRow(10L, "T1", 2),
				new PollOptionRow(11L, "GEN", 1)));
		lenient().when(repository.countVoters(5L)).thenReturn(3);
	}

	@Test
	void 선택지_갯수와_길이_마감시간을_검증한다() {
		assertThatThrownBy(() -> service.attachPoll(1L,
				new PollCreateRequest("q", List.of("하나"), null, null, null)))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.attachPoll(1L,
				new PollCreateRequest("q", List.of("1", "2", "3", "4", "5"), null, null, null)))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.attachPoll(1L,
				new PollCreateRequest("q", List.of("a", "b"), null, null, 0)))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.attachPoll(1L,
				new PollCreateRequest("q", List.of("a", "b"), null, null, 169)))
				.isInstanceOf(CustomException.class);
		verify(repository, never()).createPoll(anyLong(), any(), any(), anyBoolean(), anyBoolean(), any());

		service.attachPoll(1L, new PollCreateRequest(" 우승팀은? ", List.of(" T1 ", "GEN"),
				true, true, 24));
		// alwaysShowResults=true → hideResultsUntilVoted=false, 마감 24시간 뒤
		verify(repository).createPoll(eq(1L), eq("우승팀은?"), eq(List.of("T1", "GEN")),
				eq(false), eq(true), any(LocalDateTime.class));
	}

	@Test
	void 미투표_숨김이면_분포는_비우고_참여수는_남긴다() {
		stubPoll(POLL);
		when(repository.findMyOptionIds(5L, 7L)).thenReturn(List.of());

		PollResponse response = service.findForViewer(1L, 7L);

		assertThat(response.resultsVisible()).isFalse();
		assertThat(response.totalVotes()).isEqualTo(3);
		assertThat(response.options()).allSatisfy(o -> assertThat(o.voteCount()).isNull());
	}

	@Test
	void 투표했으면_분포가_보인다() {
		stubPoll(POLL);
		when(repository.findMyOptionIds(5L, 7L)).thenReturn(List.of(10L));

		PollResponse response = service.findForViewer(1L, 7L);

		assertThat(response.resultsVisible()).isTrue();
		assertThat(response.myOptionIds()).containsExactly(10L);
		assertThat(response.options().get(0).voteCount()).isEqualTo(2);
	}

	@Test
	void 마감되면_투표는_막히고_분포는_공개된다() {
		PollRow closed = new PollRow(5L, 1L, "우승팀은?", true, false,
				LocalDateTime.now().minusHours(1), 3);
		stubPoll(closed);
		when(repository.findMyOptionIds(5L, 7L)).thenReturn(List.of());

		PollResponse response = service.findForViewer(1L, 7L);
		assertThat(response.closed()).isTrue();
		assertThat(response.resultsVisible()).isTrue(); // 마감 후엔 숨길 이유가 없다

		assertThatThrownBy(() -> service.vote(1L, 7L, 10L))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_POLL_CLOSED));
		verify(repository, never()).insertVote(anyLong(), anyLong(), anyLong());
	}

	@Test
	void 단일_선택은_다른_선택지_두번째_표도_409다() {
		stubPoll(POLL);
		when(repository.optionBelongsToPoll(11L, 5L)).thenReturn(true);
		when(repository.findMyOptionIds(5L, 7L)).thenReturn(List.of(10L)); // 이미 T1 에 투표

		assertThatThrownBy(() -> service.vote(1L, 7L, 11L))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_ALREADY_VOTED));
		verify(repository, never()).insertVote(anyLong(), anyLong(), anyLong());
	}

	@Test
	void 복수_선택은_다른_선택지에_추가_투표가_되고_같은_선택지는_409다() {
		PollRow multiple = new PollRow(5L, 1L, "우승팀은?", true, true, null, 3);
		stubPoll(multiple);
		when(repository.optionBelongsToPoll(11L, 5L)).thenReturn(true);
		when(repository.insertVote(5L, 11L, 7L)).thenReturn(true);
		when(repository.findMyOptionIds(5L, 7L)).thenReturn(List.of(10L, 11L));

		PollResponse response = service.vote(1L, 7L, 11L);
		verify(repository).applyVote(5L, 11L);
		assertThat(response.myOptionIds()).containsExactly(10L, 11L);

		// 같은 선택지 재투표 — uk(poll, member, option) 가 막고 409 로 변환
		when(repository.insertVote(5L, 11L, 7L)).thenReturn(false);
		assertThatThrownBy(() -> service.vote(1L, 7L, 11L))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_ALREADY_VOTED));
	}

	@Test
	void 남의_투표_선택지로는_투표할_수_없다() {
		stubPoll(POLL);
		when(repository.optionBelongsToPoll(99L, 5L)).thenReturn(false);

		assertThatThrownBy(() -> service.vote(1L, 7L, 99L)).isInstanceOf(CustomException.class);
		verify(repository, never()).insertVote(anyLong(), anyLong(), anyLong());
	}
}
