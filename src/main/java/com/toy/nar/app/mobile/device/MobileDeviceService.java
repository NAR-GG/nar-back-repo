package com.toy.nar.app.mobile.device;

import com.toy.nar.app.mobile.device.dto.MobileDeviceRegistrationRequest;
import com.toy.nar.app.mobile.device.dto.MobileDeviceResponse;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberDevice;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileDeviceService {

	private final MemberRepository memberRepository;
	private final MemberDeviceRepository deviceRepository;

	@Transactional
	public MobileDeviceResponse register(
			Long memberId,
			MobileDeviceRegistrationRequest request) {
		Member member = requireMember(memberId);
		MemberDevice device = deviceRepository.findByFcmToken(request.fcmToken())
				.orElseGet(() -> MemberDevice.builder()
						.member(member)
						.fcmToken(request.fcmToken())
						.platform(request.platform())
						.build());
		device.register(member, request.platform());
		return MobileDeviceResponse.from(deviceRepository.save(device));
	}

	@Transactional
	public void deactivate(Long memberId, Long deviceId) {
		requireMemberId(memberId);
		MemberDevice device = deviceRepository.findByIdAndMember_Id(deviceId, memberId)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND,
						"등록된 기기를 찾을 수 없습니다."));
		device.deactivate();
	}

	private Member requireMember(Long memberId) {
		requireMemberId(memberId);
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND,
						"회원을 찾을 수 없습니다."));
	}

	private void requireMemberId(Long memberId) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
	}
}
