package com.toy.nar.app.mobile.notification;

import com.toy.nar.app.mobile.notification.dto.QuietHoursResponse;
import com.toy.nar.app.mobile.notification.dto.QuietHoursUpdateRequest;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class MobileQuietHoursService {

	private final MemberRepository memberRepository;

	@Transactional(readOnly = true)
	public QuietHoursResponse get(Long memberId) {
		return toResponse(findMember(memberId));
	}

	@Transactional
	public QuietHoursResponse update(Long memberId, QuietHoursUpdateRequest request) {
		Member member = findMember(memberId);
		if (request.enabled()) {
			validate(request.startTime(), request.endTime());
		}
		member.updateQuietHours(request.enabled(), request.startTime(), request.endTime());
		return toResponse(member);
	}

	/**
	 * 시작과 종료가 같으면 판정식이 "24시간 무음" 으로 빠지는데 유저는 그걸 의도하지 않았고
	 * 증상이 조용해서 원인을 못 찾는다. 그래서 거부한다.
	 * 분은 앱이 5분 스텝으로 고르므로 계약만 확인한다.
	 */
	private void validate(LocalTime startTime, LocalTime endTime) {
		if (startTime.equals(endTime)) {
			throw new CustomException(ErrorCode.INVALID_QUIET_HOURS);
		}
		if (startTime.getMinute() % 5 != 0 || endTime.getMinute() % 5 != 0) {
			throw new CustomException(ErrorCode.INVALID_QUIET_HOURS);
		}
	}

	/**
	 * 같은 패키지 {@code MobileTeamNotificationService#requireMember} 와 동일한 처리다.
	 * {@code ErrorCode} 에 회원 미존재 상수가 없어 형제 서비스 패턴을 따른다.
	 */
	private Member findMember(Long memberId) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
	}

	private QuietHoursResponse toResponse(Member member) {
		return new QuietHoursResponse(
				member.isQuietHoursEnabled(),
				member.getQuietStartTime(),
				member.getQuietEndTime());
	}
}
