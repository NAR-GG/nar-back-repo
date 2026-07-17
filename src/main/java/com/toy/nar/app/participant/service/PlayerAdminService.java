package com.toy.nar.app.participant.service;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

/**
 * 백오피스 선수 수정. LCK 출전 이력이 있는 선수만 허용(서버 강제 — UI 필터만 믿지 않는다).
 * 이미지 수정은 overrideImage로 잠금까지 걸어 자동 동기화 덮어쓰기를 차단한다.
 */
@Service
@RequiredArgsConstructor
public class PlayerAdminService {

	private static final String EDITABLE_LEAGUE = "LCK";

	private final PlayerRepository playerRepository;
	private final TeamRepository teamRepository;

	@Transactional
	public Player update(Long playerId, String imageUrl, Boolean unlockImage, Long currentTeamId) {
		Player player = playerRepository.findById(playerId)
				.orElseThrow(() -> new NoSuchElementException("선수를 찾을 수 없습니다: " + playerId));
		if (!playerRepository.hasLeagueParticipation(playerId, EDITABLE_LEAGUE)) {
			throw new IllegalStateException("LCK 출전 이력이 있는 선수만 수정할 수 있습니다");
		}
		if (Boolean.TRUE.equals(unlockImage)) {
			player.unlockImage();
		} else if (imageUrl != null && !imageUrl.isBlank()) {
			player.overrideImage(imageUrl.trim());
		}
		if (currentTeamId != null) {
			Team team = teamRepository.findById(currentTeamId)
					.orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다: " + currentTeamId));
			player.changeCurrentTeam(team);
		}
		return player;
	}
}
