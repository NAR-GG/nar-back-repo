package com.toy.nar.domain.participant;

import java.util.List;
import java.util.Locale;

/** 선수 포지션 정렬 순서(탑→정글→미드→바텀→서포터). role 값은 players.role(Top/Jungle/Mid/ADC/Support). */
public final class PlayerRoleOrder {

	private static final List<String> ORDER = List.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT");

	private PlayerRoleOrder() {
	}

	/** 정렬 인덱스. 알 수 없거나 null 인 포지션은 맨 뒤로. */
	public static int of(String role) {
		if (role == null) {
			return ORDER.size();
		}
		int index = ORDER.indexOf(role.toUpperCase(Locale.ROOT));
		return index < 0 ? ORDER.size() : index;
	}
}
