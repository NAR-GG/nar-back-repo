package com.toy.nar.app.lolesports.live;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuneMetadataResolverTest {

	/** 결의(8400) 4개 + 영감(8300) 2개. 슬롯은 결의: 8437=0, 8446=1, 8444=2, 8242=3 / 영감: 8345=1, 8347=2. */
	private static RuneMetadataResolver.Catalog catalog() {
		RuneMetadataResolver.Catalog c = RuneMetadataResolver.Catalog.empty();
		c.styleNamesById().putAll(Map.of(8400, "결의", 8300, "영감"));
		c.styleIconsById().putAll(Map.of(8400, "icon/resolve", 8300, "icon/inspiration"));
		for (int id : new int[] { 8437, 8446, 8444, 8242 }) {
			c.runeStyleById().put(id, 8400);
		}
		for (int id : new int[] { 8345, 8347 }) {
			c.runeStyleById().put(id, 8300);
		}
		c.runeSlotById().putAll(Map.of(8437, 0, 8446, 1, 8444, 2, 8242, 3, 8345, 1, 8347, 2));
		c.runeNamesById().putAll(Map.of(8437, "착취의 손아귀", 8446, "철거", 8444, "재생의 바람",
				8242, "불굴의 의지", 8345, "비스킷 배달", 8347, "우주적 통찰력"));
		c.runeIconsById().put(8437, "icon/grasp");
		c.runeDescriptionsById().put(8437, "4초마다 적 챔피언 기본 공격 시 추가 마법 피해");
		return c;
	}

	@Test
	void assemble_트리_소속으로_주_부를_가르고_핵심룬을_앞에_둔다() {
		// 피드 배열은 핵심룬이 앞이지만, 순서를 뒤섞어도 소속·슬롯으로 복원돼야 한다.
		RuneMetadataResolver.RuneBuild build = RuneMetadataResolver.assemble(8400, 8300,
				List.of(8347, 8446, 8437, 8345, 8242, 8444, 5008, 5011),
				catalog());

		assertThat(build.primary().styleName()).isEqualTo("결의");
		assertThat(build.primary().runes())
				.extracting(RuneMetadataResolver.Rune::name)
				.containsExactly("착취의 손아귀", "철거", "재생의 바람", "불굴의 의지");
		assertThat(build.primary().runes().get(0).iconUrl()).isEqualTo("icon/grasp");
		assertThat(build.primary().runes().get(0).description()).isEqualTo("4초마다 적 챔피언 기본 공격 시 추가 마법 피해");

		assertThat(build.sub().styleName()).isEqualTo("영감");
		assertThat(build.sub().styleIconUrl()).isEqualTo("icon/inspiration");
		assertThat(build.sub().runes())
				.extracting(RuneMetadataResolver.Rune::name)
				.containsExactly("비스킷 배달", "우주적 통찰력");
	}

	@Test
	void assemble_파편은_이름_아이콘_수치로_나가고_2개면_2개다() {
		// 같은 파편을 두 칸에 찍으면 피드가 하나로 합친다(25%). 3개로 채워 넣지 않는다.
		RuneMetadataResolver.RuneBuild build = RuneMetadataResolver.assemble(8400, 8300,
				List.of(8437, 5008, 5011), catalog());

		assertThat(build.shards()).hasSize(2);
		assertThat(build.shards().get(0).name()).isEqualTo("적응형 능력치");
		assertThat(build.shards().get(0).label()).isEqualTo("+9");
		assertThat(build.shards().get(0).iconUrl()).endsWith("statmodsadaptiveforceicon.png");
		assertThat(build.shards().get(1).name()).isEqualTo("체력");
		assertThat(build.shards().get(1).label()).isEqualTo("+10~180");
	}

	@Test
	void assemble_메타에_없는_룬은_배열_순서로_주4_부2_폴백한다() {
		// ddragon 미반영 신규 룬. 빈 카탈로그에서도 6개가 4/2 로 갈려야 한다.
		RuneMetadataResolver.RuneBuild build = RuneMetadataResolver.assemble(8000, 8100,
				List.of(1, 2, 3, 4, 5, 6), RuneMetadataResolver.Catalog.empty());

		assertThat(build.primary().runes()).hasSize(4);
		assertThat(build.sub().runes()).hasSize(2);
		assertThat(build.primary().runes().get(0).name()).isEqualTo("룬-1");
	}

	@Test
	void stripMarkup_ddragon_마크업을_벗겨_평문만_남긴다() {
		String html = "3초 동안 <b>개별</b> 스킬 3회 적중 시 추가 "
				+ "<lol-uikit-tooltipped-keyword key='X'><font color='#48C4B7'>적응형 피해</font></lol-uikit-tooltipped-keyword>"
				+ "<br><br>재사용 대기시간: 20초";

		assertThat(RuneMetadataResolver.stripMarkup(html))
				.isEqualTo("3초 동안 개별 스킬 3회 적중 시 추가 적응형 피해 재사용 대기시간: 20초");
		assertThat(RuneMetadataResolver.stripMarkup("")).isNull();
		assertThat(RuneMetadataResolver.stripMarkup(null)).isNull();
	}

	@Test
	void 파편_이름은_기존_runeNames_경로에서도_수치가_붙어_나온다() {
		// 5011 이 "강인함" 으로 잘못 적혀 있던 옛 표를 바로잡았다 — 5011 은 체력(성장), 강인함은 5013.
		RuneMetadataResolver.Catalog c = RuneMetadataResolver.Catalog.empty();
		assertThat(c.runeName(5011)).isEqualTo("체력 +10~180");
		assertThat(c.runeName(5013)).isEqualTo("강인함 및 둔화 저항 +10%");
		assertThat(c.runeName(5001)).isEqualTo("체력 증가 +65");
	}
}
