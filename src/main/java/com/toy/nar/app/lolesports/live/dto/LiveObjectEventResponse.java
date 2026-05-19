package com.toy.nar.app.lolesports.live.dto;

import java.time.LocalDateTime;

public record LiveObjectEventResponse(
		String teamSide,
		String eventType,
		String eventSubType,
		Integer eventOrder,
		Integer valueAfter,
		LocalDateTime sourceFrameTimestampUtc) {
}
