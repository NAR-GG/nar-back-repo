package com.toy.nar.app.riot.dto;

import java.util.List;

public record RiotMatchResponse(
		Metadata metadata,
		Info info) {

	public record Metadata(
			String matchId) {
	}

	public record Info(
			Integer queueId,
			Long gameStartTimestamp,
			Long gameEndTimestamp,
			List<Participant> participants) {

		/**
		 * 경기 길이(초). 두 타임스탬프 차이로 구한다.
		 *
		 * <p>{@code info.gameDuration} 을 쓰지 않는 이유: 11.20 패치 이전 매치는 밀리초,
		 * 이후는 초로 내려와 단위가 갈린다(판별 기준이 {@code gameEndTimestamp} 존재 여부다).
		 * 두 타임스탬프는 둘 다 epoch 밀리초라 모호함이 없다.</p>
		 *
		 * @return 값이 없거나 음수면 null
		 */
		public Integer durationSeconds() {
			if (gameStartTimestamp == null || gameEndTimestamp == null) {
				return null;
			}
			long millis = gameEndTimestamp - gameStartTimestamp;
			return millis <= 0 ? null : (int) (millis / 1000);
		}
	}

	public record Participant(
			String puuid,
			Integer championId,
			Boolean win,
			Integer kills,
			Integer deaths,
			Integer assists) {
	}
}
