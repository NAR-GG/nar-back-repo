package com.toy.nar.app.standings.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;

/**
 * 리그 순위표 응답.
 *
 * <p>그룹이 없는 리그(LEC·LCS·CBLOL)도 {@code groups} 길이 1 로 내려간다. 앱은 {@code name} 이
 * 있을 때만 그룹 헤더를 그리면 되고 행 렌더링은 같다 — 템플릿을 두 벌 만들 필요가 없다.
 */
@Builder
public record StandingsResponse(
		String league,

		/** 순위표를 줄 수 있는 리그인지. false 면 {@code groups} 는 비어 있다. */
		boolean supported,

		/** 미지원 사유. {@code BRACKET_ONLY}(스위스·토너먼트라 순위가 없음), {@code UNAVAILABLE}(조회 실패). */
		String reason,

		/** 화면에 쓸 집계 범위 라벨. 네이버 bracketName 은 통산 표에도 "정규시즌 1-2R"이 붙어 와서 쓰지 않는다. */
		String scopeLabel,

		/** 정규 일정이 모두 끝났는지. true 면 순위가 더 안 바뀌고 앱은 "정규 종료" 배지를 띄운다. */
		boolean regularFinished,

		/**
		 * 어느 경기까지 반영됐는지. "언제 조회했나"가 아니라 "데이터가 어디까지 찼나"다.
		 * 리그 휴식기에는 며칠 전 값이 정상이므로 경과 시간으로 환산해 보여주면 안 된다.
		 */
		LocalDateTime dataThrough,

		/**
		 * 네이버 집계와 우리 DB 집계의 경기 수가 맞는지.
		 *
		 * <p>경기가 끝난 직후 몇 시간은 네이버가 먼저 갱신되고 우리 DB 가 뒤따르는 구간이 있다.
		 * 그 사이에는 승패(네이버)와 세트 득실·연속(우리 DB)의 기준 시점이 어긋난다.
		 * false 면 앱이 파생 컬럼에 "갱신 중"을 표시한다.
		 */
		boolean inSync,

		List<Group> groups) {

	@Builder
	public record Group(String name, List<Row> rows) {
	}

	/**
	 * 순위 한 줄.
	 *
	 * <p>{@code rank} 는 네이버 값 그대로다. 동률이면 같은 값이 반복되고(공동 순위) 다음 순위는
	 * 그만큼 건너뛴다 — LEC 에서 공동 4위 둘 다음이 6위인 사례를 확인했다. 앱은 이 값을 그대로
	 * 찍되 중복이면 공동임을 표시한다.
	 */
	@Builder
	public record Row(
			int rank,
			String teamCode,
			String teamName,
			String imageUrl,
			int wins,
			int losses,

			/** 세트 득실차. 네이버가 주는 값. */
			int setDiff,

			/** 세트 승/패 원값. 네이버가 안 줘서 우리 DB 로 계산한다. 미집계면 null. */
			Integer setWins,
			Integer setLosses,

			/** 연속 기록. 승이면 양수, 패면 음수. 계산 못 하면 null. */
			Integer streak,

			/** 남은 정규 경기 수. */
			Integer remaining) {
	}
}
