package com.toy.nar.app.member.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 백오피스 회원 삭제. member_id FK로 참조하는 자식 테이블을 먼저 지우고 회원을 지운다.
 * ponytail: 자식 테이블 목록 하드코딩. member 참조 엔티티가 늘면 여기에도 추가해야 한다
 * (누락 시 FK 위반 → 컨트롤러에서 409로 표면화되므로 조용히 깨지진 않는다).
 */
@Service
@RequiredArgsConstructor
public class MemberDeleteService {

	// 삭제 순서 무관(모두 member_id 직접 참조, 상호 FK 없음).
	private static final List<String> MEMBER_CHILD_TABLES = List.of(
			"refresh_token",
			"member_social",
			"member_device",
			"member_favorite_player",
			"member_notification",
			"member_match_subscription",
			"member_team_notification_subscription",
			"player_solo_rank_push_delivery",
			"member_team_event_push_delivery",
			"live_player_rating");

	private final JdbcTemplate jdbcTemplate;
	private final MemberRepository memberRepository;

	@Transactional
	public void delete(Long memberId) {
		if (!memberRepository.existsById(memberId)) {
			throw new NoSuchElementException("회원을 찾을 수 없습니다: " + memberId);
		}
		for (String table : MEMBER_CHILD_TABLES) {
			jdbcTemplate.update("DELETE FROM " + table + " WHERE member_id = ?", memberId);
		}
		memberRepository.deleteById(memberId);
	}
}
