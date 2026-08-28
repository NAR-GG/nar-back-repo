package com.toy.nar.domain.member.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CommunityWriteBlockedException;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.participant.entity.Team;

/**
 * 응원팀 변경 쿨다운 — 한 번 바꾸면 30일간 다시 못 바꾼다.
 *
 * <p>원래 이 쿨다운은 <b>팀 게시판 쓰기</b>를 막는 쪽이었다(D-1). 그러면 팀을 바꾼
 * 사용자가 자기 팀 게시판에 들어가서야 "30일 뒤에 쓸 수 있다"는 걸 알게 된다 —
 * 이미 바꾼 뒤라 되돌릴 수도 없다. 막을 거면 바꾸기 전에 막아야 하므로 게이트를
 * 변경 시점으로 옮겼다. 팀 갈아타기를 30일에 한 번으로 제한한다는 목적은 같고,
 * 응원팀이면 자기 게시판에는 언제나 쓸 수 있다.</p>
 *
 * <p>최초 선택은 변경이 아니다({@code favoriteTeamChangedAt == null}). 같은 팀을
 * 다시 고르는 것도 변경이 아니라 통과시킨다 — 이름·사진만 바꾸는 저장이 팀 때문에
 * 막히면 안 된다.</p>
 */
@Component
public class FavoriteTeamChangePolicy {

	@Value("${community.team-change-cooldown-days:30}")
	private long cooldownDays = 30;

	/** 다음에 팀을 바꿀 수 있는 시각. null 이면 지금 바꿀 수 있다. */
	public LocalDateTime changeAvailableFrom(Member member) {
		LocalDateTime changedAt = member.getFavoriteTeamChangedAt();
		if (changedAt == null) {
			return null;
		}
		LocalDateTime availableFrom = changedAt.plusDays(cooldownDays);
		return remainingSeconds(availableFrom) > 0 ? availableFrom : null;
	}

	/**
	 * [newTeam] 으로 바꿀 수 있는지 검사한다. 쿨다운 중이면 403 + Retry-After.
	 * 팀이 안 바뀌는 요청은 검사 대상이 아니다.
	 */
	public void checkChangeable(Member member, Team newTeam) {
		Long currentId = member.getFavoriteTeam() == null ? null : member.getFavoriteTeam().getId();
		Long newId = newTeam == null ? null : newTeam.getId();
		if (currentId == null || currentId.equals(newId)) {
			return;
		}
		LocalDateTime availableFrom = changeAvailableFrom(member);
		if (availableFrom != null) {
			throw new CommunityWriteBlockedException(ErrorCode.FAVORITE_TEAM_CHANGE_COOLDOWN,
					remainingSeconds(availableFrom));
		}
	}

	private static long remainingSeconds(LocalDateTime until) {
		return Duration.between(LocalDateTime.now(), until).getSeconds();
	}
}
