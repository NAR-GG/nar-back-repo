package com.toy.nar.app.lolesports.live;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMetadataResolverTest {

	private static ItemMetadataResolver.ResolvedItem core(String url) {
		return new ItemMetadataResolver.ResolvedItem(url, Set.of("Damage"));
	}

	private static ItemMetadataResolver.ResolvedItem boots(String url) {
		return new ItemMetadataResolver.ResolvedItem(url, Set.of("Boots", "NonbootsMovement"));
	}

	private static ItemMetadataResolver.ResolvedItem trinket(String url) {
		return new ItemMetadataResolver.ResolvedItem(url, Set.of("Active", "Trinket", "Vision"));
	}

	private static ItemMetadataResolver.ResolvedItem consumable(String url) {
		return new ItemMetadataResolver.ResolvedItem(url, Set.of("Consumable", "Vision"));
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
