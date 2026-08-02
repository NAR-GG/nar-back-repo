package com.toy.nar.app.player;

import com.toy.nar.domain.participant.LckTeamCatalog;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LoL Esports getTeams 로스터와 DB {@code current_team_id}를 대조해 이적 의심 선수를 찾는다.
 *
 * <p>DB에 자동으로 쓰지 않는다 — 알림만 보내고 백오피스에서 사람이 확정한다. getTeams 로스터는
 * 1군/2군 구분이 없어서(같은 코드의 LCK 챌린저스 팀 로스터가 1군과 완전히 동일) 자동 반영하면
 * 아카데미 선수가 1군으로 승격되는 오탐이 대량 발생한다.
 *
 * <p>대상을 "소속팀이 이미 LCK 1군인 선수"로 좁힌 것도 같은 이유다. 2026-08 기준 이 조건 없이
 * 전체를 대조하면 114명 중 61명이 diff로 뜨고 그중 54명이 2군 선수 오탐이었다. 좁히면 실제
 * 1군 간 이적만 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LckRosterDiffService {

	private final LolesportsPlayerImageClient lolesportsPlayerImageClient;
	private final PlayerRepository playerRepository;

	/** 선수명, DB 소속팀 코드, getTeams 로스터 팀 코드. */
	public record RosterDiff(String playerName, String currentTeamCode, String rosterTeamCode) {
	}

	@Transactional(readOnly = true)
	public List<RosterDiff> detect() {
		Map<String, String> rosterByPlayerName = lolesportsPlayerImageClient.fetchLckFirstTeamRosters();
		if (rosterByPlayerName.isEmpty()) {
			log.warn("LCK roster diff skipped: getTeams returned no LCK first-team roster");
			return List.of();
		}

		List<RosterDiff> diffs = new ArrayList<>();
		for (Player player : playerRepository.findByCurrentTeamCodeIn(LckTeamCatalog.TEAM_CODES)) {
			Team currentTeam = player.getCurrentTeam();
			if (currentTeam == null || currentTeam.getCode() == null) {
				continue;
			}
			String currentCode = currentTeam.getCode().toUpperCase(Locale.ROOT);
			String rosterCode = rosterByPlayerName.get(player.getName().trim().toLowerCase(Locale.ROOT));

			// ponytail: 로스터에 없는 선수(은퇴·해외 이적)는 넘긴다. 팀 이동만 본다.
			if (rosterCode == null || rosterCode.equals(currentCode)) {
				continue;
			}
			diffs.add(new RosterDiff(player.getName(), currentCode, rosterCode));
		}
		return diffs;
	}
}
