package com.toy.nar.app.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

	private static final PollRow POLL = new PollRow(5L, 1L, "우승팀은?", true, 3);

	private void stubPoll() {
		lenient().when(repository.findByPostId(1L)).thenReturn(Optional.of(POLL));
		lenient().when(repository.findOptions(5L)).thenReturn(List.of(
				new PollOptionRow(10L, "T1", 2),
				new PollOptionRow(11L, "GEN", 1)));
	}

	@Test
	void 선택지_갯수와_길이를_검증한다() {
		assertThatThrownBy(() -> service.attachPoll(1L, new PollCreateRequest("q", List.of("하나"))))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.attachPoll(1L,
				new PollCreateRequest("q", List.of("1", "2", "3", "4", "5"))))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.attachPoll(1L, new PollCreateRequest("q", List.of("a", " "))))
				.isInstanceOf(CustomException.class);
		assertThatThrownBy(() -> service.attachPoll(1L,
				new PollCreateRequest("가".repeat(101), List.of("a", "b"))))
				.isInstanceOf(CustomException.class);
		verify(repository, never()).createPoll(anyLong(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());

		service.attachPoll(1L, new PollCreateRequest(" 우승팀은? ", List.of(" T1 ", "GEN")));
		verify(repository).createPoll(1L, "우승팀은?", List.of("T1", "GEN"));
	}

	@Test
	void 미투표_숨김이면_분포는_비우고_참여수는_남긴다() {
		stubPoll();
		when(repository.findMyOptionId(5L, 7L)).thenReturn(null);

		PollResponse response = service.findForViewer(1L, 7L);

		assertThat(response.resultsVisible()).isFalse();
		assertThat(response.totalVotes()).isEqualTo(3);
		assertThat(response.options()).allSatisfy(o -> assertThat(o.voteCount()).isNull());
	}

	@Test
	void 투표했으면_분포가_보인다() {
		stubPoll();
		when(repository.findMyOptionId(5L, 7L)).thenReturn(10L);

		PollResponse response = service.findForViewer(1L, 7L);

		assertThat(response.resultsVisible()).isTrue();
		assertThat(response.myOptionId()).isEqualTo(10L);
		assertThat(response.options().get(0).voteCount()).isEqualTo(2);
	}

	@Test
	void 재투표는_409_변경불가() {
		stubPoll();
		when(repository.optionBelongsToPoll(10L, 5L)).thenReturn(true);
		when(repository.insertVote(5L, 10L, 7L)).thenReturn(false);

		assertThatThrownBy(() -> service.vote(1L, 7L, 10L))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_ALREADY_VOTED));
		verify(repository, never()).applyVote(anyLong(), anyLong());
	}

	@Test
	void 남의_투표_선택지로는_투표할_수_없다() {
		stubPoll();
		when(repository.optionBelongsToPoll(99L, 5L)).thenReturn(false);

		assertThatThrownBy(() -> service.vote(1L, 7L, 99L)).isInstanceOf(CustomException.class);
		verify(repository, never()).insertVote(anyLong(), anyLong(), anyLong());
	}

	@Test
	void 정상_투표는_기록과_카운터를_남기고_투표후_상태를_돌려준다() {
		stubPoll();
		when(repository.optionBelongsToPoll(10L, 5L)).thenReturn(true);
		when(repository.insertVote(5L, 10L, 7L)).thenReturn(true);
		when(repository.findMyOptionId(5L, 7L)).thenReturn(10L);

		PollResponse response = service.vote(1L, 7L, 10L);

		verify(repository).applyVote(5L, 10L);
		assertThat(response.myOptionId()).isEqualTo(10L);
		assertThat(response.resultsVisible()).isTrue();
	}
}
