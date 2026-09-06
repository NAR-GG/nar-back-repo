package com.toy.nar.app.lolesports.live;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMetadataResolverTest {

	private static ItemMetadataResolver.ResolvedItem core(String url) {
		return new ItemMetadataResolver.ResolvedItem(url + "-name", url, url + "-desc", Set.of("Damage"));
	}

	private static ItemMetadataResolver.ResolvedItem boots(String url) {
		return new ItemMetadataResolver.ResolvedItem(url + "-name", url, url + "-desc",
				Set.of("Boots", "NonbootsMovement"));
	}

	private static ItemMetadataResolver.ResolvedItem trinket(String url) {
		return new ItemMetadataResolver.ResolvedItem(url + "-name", url, url + "-desc",
				Set.of("Active", "Trinket", "Vision"));
	}

	private static ItemMetadataResolver.ResolvedItem consumable(String url) {
		return new ItemMetadataResolver.ResolvedItem(url + "-name", url, url + "-desc",
				Set.of("Consumable", "Vision"));
	}

	@Test
	void groupItems_코어가_7개면_신발을_퀘스트_칸으로_뺀다() {
		// 원딜 퀘스트 완료: 피드 배열은 구매 순서라 신발이 3번째에 있다(실측). 마지막 템이 아니라 신발이 퀘스트 칸.
		ItemMetadataResolver.ItemGroups groups = ItemMetadataResolver.groupItems(List.of(
				core("i1"), core("i2"), boots("boots"), trinket("t"),
				core("i3"), core("i4"), core("i5"), core("i6"), consumable("ward")));

		assertThat(groups.coreImageUrls()).containsExactly("i1", "i2", "i3", "i4", "i5", "i6");
		assertThat(groups.questItemImageUrl()).isEqualTo("boots");
		assertThat(groups.trinketImageUrl()).isEqualTo("t");
		assertThat(groups.consumableImageUrls()).containsExactly("ward");
		// 이름·설명도 같은 칸으로 따라온다 (빌드 시트 상세용)
		assertThat(groups.questItem().name()).isEqualTo("boots-name");
		assertThat(groups.questItem().description()).isEqualTo("boots-desc");
		assertThat(groups.trinket().name()).isEqualTo("t-name");
		assertThat(groups.core().get(0).description()).isEqualTo("i1-desc");
	}

	@Test
	void toMultilineText_스탯_효과이름_본문을_줄과_단락으로_가른다() {
		// 실제 ddragon 3073(실험적 마공학판) 마크업 축약. <br>=줄바꿈, <br><br>=단락.
		String html = "<mainText><stats>공격력 <attention>40</attention><br>공격 속도 <attention>20%</attention></stats>"
				+ "<br><br><passive>마공학 충전</passive><br>궁극기 가속이 30 증가합니다.<br><br>"
				+ "<passive>폭주</passive><br>8초 동안 <attackSpeed>공격 속도가 0%</attackSpeed> 증가합니다. </mainText>";

		assertThat(ItemMetadataResolver.toMultilineText(html)).isEqualTo(
				"공격력 40\n공격 속도 20%"
						+ "\n\n마공학 충전\n궁극기 가속이 30 증가합니다."
						+ "\n\n폭주\n8초 동안 공격 속도가 0% 증가합니다.");
	}

	@Test
	void toMultilineText_스탯이_빈_장신구는_앞_빈줄_없이_본문부터_시작한다() {
		// 3364(예언자의 렌즈): <stats></stats><br><br> 로 시작한다. 선행 단락 구분이 남으면 카드 위가 뜬다.
		String html = "<mainText><stats></stats><br><br><active>사용 시</active> (160초)<br>주변 와드를 드러냅니다.</mainText>";

		assertThat(ItemMetadataResolver.toMultilineText(html))
				.isEqualTo("사용 시 (160초)\n주변 와드를 드러냅니다.");
	}

	@Test
	void toMultilineText_null_이나_태그만_있으면_null() {
		assertThat(ItemMetadataResolver.toMultilineText(null)).isNull();
		assertThat(ItemMetadataResolver.toMultilineText("<mainText><stats></stats></mainText>")).isNull();
	}

	@Test
	void groupItems_코어가_6개_이하면_신발도_코어에_그대로_둔다() {
		// 퀘스트 미완: 신발은 평범한 코어 한 칸이다.
		ItemMetadataResolver.ItemGroups groups = ItemMetadataResolver.groupItems(List.of(
				core("i1"), boots("boots"), core("i2")));

		assertThat(groups.coreImageUrls()).containsExactly("i1", "boots", "i2");
		assertThat(groups.questItemImageUrl()).isNull();
	}

	@Test
	void groupItems_장신구_소모품_없으면_null_과_빈_목록이다() {
		ItemMetadataResolver.ItemGroups groups = ItemMetadataResolver.groupItems(List.of(core("i1"), core("i2")));

		assertThat(groups.coreImageUrls()).containsExactly("i1", "i2");
		assertThat(groups.questItemImageUrl()).isNull();
		assertThat(groups.trinketImageUrl()).isNull();
		assertThat(groups.consumableImageUrls()).isEmpty();
	}

	@Test
	void groupItems_장신구가_둘_남으면_먼저_산_쪽만_남긴다() {
		// items 배열엔 판매·교체 잔재가 남을 수 있다(신발 2종 동시 보유 같은 사례가 실제로 있다).
		ItemMetadataResolver.ItemGroups groups = ItemMetadataResolver.groupItems(List.of(
				trinket("first"), core("i1"), trinket("second")));

		assertThat(groups.trinketImageUrl()).isEqualTo("first");
		assertThat(groups.coreImageUrls()).containsExactly("i1");
	}

	@Test
	void groupItems_코어가_7개인데_신발이_없으면_7번째를_퀘스트_칸으로_쓰고_나머지는_버린다() {
		// 실측 0건인 방어 경로. 신발 없이 7개면 배열 순서대로 7번째, 그 뒤는 하위템 잔재로 버린다.
		ItemMetadataResolver.ItemGroups groups = ItemMetadataResolver.groupItems(List.of(
				core("i1"), core("i2"), core("i3"), core("i4"), core("i5"), core("i6"),
				core("seventh"), core("leftover")));

		assertThat(groups.coreImageUrls()).containsExactly("i1", "i2", "i3", "i4", "i5", "i6");
		assertThat(groups.questItemImageUrl()).isEqualTo("seventh");
	}
}
