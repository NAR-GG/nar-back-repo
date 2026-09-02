package com.toy.nar.app.lolesports.live;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMetadataResolverTest {

	private static ItemMetadataResolver.ResolvedItem core(String url) {
		return new ItemMetadataResolver.ResolvedItem(url, Set.of("Damage"));
	}

	private static ItemMetadataResolver.ResolvedItem trinket(String url) {
		return new ItemMetadataResolver.ResolvedItem(url, Set.of("Active", "Trinket", "Vision"));
	}

	private static ItemMetadataResolver.ResolvedItem consumable(String url) {
		return new ItemMetadataResolver.ResolvedItem(url, Set.of("Consumable", "Vision"));
	}

	@Test
	void groupItems_코어_6개까지_채우고_7번째는_퀘스트_칸으로_뺀다() {
		// 원딜 퀘스트 케이스: 신발 포함 코어 7 + 장신구 + 제어와드.
		ItemMetadataResolver.ItemGroups groups = ItemMetadataResolver.groupItems(List.of(
				core("i1"), core("i2"), core("i3"), core("i4"),
				trinket("t"), core("i5"), core("i6"), core("quest"), consumable("ward")));

		assertThat(groups.coreImageUrls()).containsExactly("i1", "i2", "i3", "i4", "i5", "i6");
		assertThat(groups.questItemImageUrl()).isEqualTo("quest");
		assertThat(groups.trinketImageUrl()).isEqualTo("t");
		assertThat(groups.consumableImageUrls()).containsExactly("ward");
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
	void groupItems_코어가_8개를_넘으면_뒤쪽_잔재는_버린다() {
		ItemMetadataResolver.ItemGroups groups = ItemMetadataResolver.groupItems(List.of(
				core("i1"), core("i2"), core("i3"), core("i4"), core("i5"), core("i6"),
				core("quest"), core("leftover")));

		assertThat(groups.coreImageUrls()).hasSize(6);
		assertThat(groups.questItemImageUrl()).isEqualTo("quest");
	}
}
