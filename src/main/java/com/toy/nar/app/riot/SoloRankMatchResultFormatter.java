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
	public static String resultLine(
			String championName,
			RiotMatchResponse.Participant tracked,
			Integer gameDurationSeconds) {
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
		// 경기 길이는 푸시 배너에도 붙여야 한다. 배너는 이 문구를 그대로 쓰고, 앱 알림함은
		// data payload 로 다시 조립하므로, 여기 빠뜨리면 리스트에만 시간이 뜬다(실측).
		// 1분 미만은 생략한다 — "0분" 은 정보가 아니라 오해를 만든다.
		if (gameDurationSeconds != null && gameDurationSeconds >= 60) {
			line.append(" · ").append(gameDurationSeconds / 60).append("분");
		}
		return line.toString();
	}

	/**
	 * 예: "18/1/11". 셋 중 하나라도 없으면 null 이다.
	 *
	 * <p>{@link #resultLine} 이 만든 한국어 문구를 앱이 그대로 쓰면 영어 로케일에서도 한국어가
	 * 나온다. 앱이 자기 문구를 조립할 수 있도록 승패·KDA 를 푸시 data 에 따로 실어준다.</p>
	 */
	public static String kda(RiotMatchResponse.Participant tracked) {
		if (tracked == null
				|| tracked.kills() == null || tracked.deaths() == null || tracked.assists() == null) {
			return null;
		}
		return tracked.kills() + "/" + tracked.deaths() + "/" + tracked.assists();
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
