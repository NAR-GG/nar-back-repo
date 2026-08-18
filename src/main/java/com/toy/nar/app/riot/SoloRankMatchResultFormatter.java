package com.toy.nar.app.riot;

import com.toy.nar.app.riot.dto.RiotMatchResponse;
import com.toy.nar.common.util.KoreanParticle;

/**
 * 솔랭 완료 매치의 결과 문구. 폴백 감지와 전이 기반 종료 알림이 같은 문구를 써야 해서 분리했다.
 */
public final class SoloRankMatchResultFormatter {

	private SoloRankMatchResultFormatter() {
	}

	/** 예: "멜로 승리 · 4/2/8", "가렌으로 패배 · 1/5/9" — 정보가 없으면 단계적으로 축약한다. */
	public static String resultLine(String championName, RiotMatchResponse.Participant tracked) {
		String champion = championName == null || championName.isBlank() ? "솔로 랭크" : championName;
		if (tracked == null) {
			return champion + " 경기 종료";
		}
		StringBuilder line = new StringBuilder(champion);
		if (tracked.win() != null) {
			line.append(KoreanParticle.ro(champion)).append(" ").append(tracked.win() ? "승리" : "패배");
		} else {
			line.append(" 경기 종료");
		}
		if (tracked.kills() != null && tracked.deaths() != null && tracked.assists() != null) {
			line.append(" · ")
					.append(tracked.kills()).append("/")
					.append(tracked.deaths()).append("/")
					.append(tracked.assists());
		}
		return line.toString();
	}

	public static RiotMatchResponse.Participant findParticipant(RiotMatchResponse match, String puuid) {
		if (match == null || match.info() == null || match.info().participants() == null) {
			return null;
		}
		return match.info().participants().stream()
				.filter(participant -> puuid.equals(participant.puuid()))
				.findFirst()
				.orElse(null);
	}

	/** match-v5 ID("KR_8292488921")를 spectator 게임 ID("8292488921") 형식으로 정규화한다. */
	public static String normalizeGameId(String matchId) {
		if (matchId == null) {
			return null;
		}
		int separatorIndex = matchId.indexOf('_');
		return separatorIndex < 0 ? matchId : matchId.substring(separatorIndex + 1);
	}
}
