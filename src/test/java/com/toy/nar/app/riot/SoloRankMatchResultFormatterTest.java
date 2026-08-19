package com.toy.nar.app.riot;

import com.toy.nar.app.riot.dto.RiotMatchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 푸시 배너 본문. 앱 알림함은 data payload 로 문구를 다시 조립하지만 배너는 이 문구를
 * 그대로 쓴다. 그래서 여기 빠뜨린 정보는 배너에서만 사라진다 — 실제로 경기 길이를
 * payload 에만 넣어 "리스트엔 시간이 뜨는데 알림엔 안 뜨는" 상태가 됐다.
 */
class SoloRankMatchResultFormatterTest {

	@Test
	@DisplayName("승패·KDA·경기 길이를 순서대로 붙인다")
	void 승패_KDA_길이_순서() {
		assertThat(SoloRankMatchResultFormatter.resultLine("베인", participant(false, 10, 5, 3), 1694))
				.isEqualTo("베인으로 패배 · 10/5/3 · 28분");
	}

	@Test
	@DisplayName("경기 길이가 없으면 KDA 까지만")
	void 길이가_없으면_생략() {
		assertThat(SoloRankMatchResultFormatter.resultLine("베인", participant(true, 10, 5, 3), null))
				.isEqualTo("베인으로 승리 · 10/5/3");
	}

	/** "0분" 은 정보가 아니라 오해를 만든다. 앱 위젯과 같은 기준이다. */
	@Test
	@DisplayName("1분 미만은 표기하지 않는다")
	void 일분_미만은_생략() {
		assertThat(SoloRankMatchResultFormatter.resultLine("베인", participant(true, 0, 0, 0), 41))
				.isEqualTo("베인으로 승리 · 0/0/0");
	}

	@Test
	@DisplayName("승패를 모르면 경기 종료로 쓰고 길이는 붙인다")
	void 승패를_모르면_경기_종료() {
		assertThat(SoloRankMatchResultFormatter.resultLine("베인", participant(null, 1, 2, 3), 1694))
				.isEqualTo("베인 경기 종료 · 1/2/3 · 28분");
	}

	/** 참가자 매칭 자체가 실패하면 KDA·길이를 실을 근거가 없다. */
	@Test
	@DisplayName("참가자가 없으면 챔피언 + 경기 종료만")
	void 참가자가_없으면() {
		assertThat(SoloRankMatchResultFormatter.resultLine("베인", null, 1694))
				.isEqualTo("베인 경기 종료");
	}

	@Test
	@DisplayName("챔피언을 모르면 솔로 랭크로 대체한다")
	void 챔피언을_모르면() {
		assertThat(SoloRankMatchResultFormatter.resultLine(null, participant(true, 4, 2, 8), 1500))
				.isEqualTo("솔로 랭크로 승리 · 4/2/8 · 25분");
	}

	private RiotMatchResponse.Participant participant(Boolean win, int k, int d, int a) {
		return new RiotMatchResponse.Participant("puuid", 1, win, k, d, a);
	}
}
