package com.toy.nar.app.lolesports;

import static org.assertj.core.api.Assertions.assertThat;

import com.toy.nar.app.lolesports.GolggKespaScoreClient.GameRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GolggKespaScoreClientTest {

	// gol.gg 매치리스트 행 구조 재현(2026-07-20 실측): 제목·좌팀·"a - b"·우팀·스테이지·패치·날짜.
	// 마지막 행은 미진행("-")이라 스킵되어야 한다.
	private static final String HTML = """
			<table><tbody>
			<tr><th>Game</th><th>Score</th><th>Patch</th><th>Date</th></tr>
			<tr>
			  <td><a href='../game/stats/80090/page-game/'>Kiwoom DRX vs KT Rolster</a></td>
			  <td>Kiwoom DRX</td><td>1 - 0</td><td>KT Rolster</td>
			  <td>GROUPS.DAY1</td><td>16.14</td><td>2026-07-20</td>
			</tr>
			<tr>
			  <td><a href='../game/stats/80089/page-game/'>NS vs BNK FearX</a></td>
			  <td>Nongshim RedForce</td><td>1 - 0</td><td>BNK FearX</td>
			  <td>GROUPS.DAY1</td><td>16.14</td><td>2026-07-20</td>
			</tr>
			<tr>
			  <td><a href='../game/stats/80088/page-game/'>DN SOOPers vs HANJIN BRION</a></td>
			  <td>HANJIN BRION</td><td>0 - 1</td><td>DN SOOPers</td>
			  <td>GROUPS.DAY1</td><td>16.14</td><td>2026-07-20</td>
			</tr>
			<tr>
			  <td><a href='../game/stats/80087/page-game/'>Kiwoom DRX vs BNK FearX</a></td>
			  <td>Kiwoom DRX</td><td>-</td><td>BNK FearX</td>
			  <td>GROUPS.DAY1</td><td>2026-07-20</td>
			</tr>
			</tbody></table>
			""";

	@Test
	void 완료된_게임만_팀코드로_파싱한다() {
		List<GameRow> rows = GolggKespaScoreClient.parse(HTML);

		// 미진행("-") 행 제외, 완료 3게임만.
		assertThat(rows).hasSize(3);

		assertThat(rows.get(0)).isEqualTo(
				new GameRow("KRX", "KT", 1, 0, LocalDate.of(2026, 7, 20)));
		assertThat(rows.get(1)).isEqualTo(
				new GameRow("NS", "BFX", 1, 0, LocalDate.of(2026, 7, 20)));
		// 좌=BRION(BRO), 우=DN SOOPers(DNS), 스코어 0-1.
		assertThat(rows.get(2)).isEqualTo(
				new GameRow("BRO", "DNS", 0, 1, LocalDate.of(2026, 7, 20)));
	}

	@Test
	void 알_수_없는_팀명은_null() {
		assertThat(GolggKespaScoreClient.toCode("Kiwoom DRX")).isEqualTo("KRX");
		assertThat(GolggKespaScoreClient.toCode("DN SOOPers")).isEqualTo("DNS");
		assertThat(GolggKespaScoreClient.toCode("T1")).isNull();
		assertThat(GolggKespaScoreClient.toCode(null)).isNull();
	}
}
