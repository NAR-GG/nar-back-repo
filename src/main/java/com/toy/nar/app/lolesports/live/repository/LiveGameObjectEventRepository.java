package com.toy.nar.app.lolesports.live.repository;

import com.toy.nar.app.lolesports.live.entity.LiveGameObjectEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LiveGameObjectEventRepository extends JpaRepository<LiveGameObjectEvent, Long> {

	boolean existsByGameIdAndTeamSideAndEventTypeAndEventOrder(
			String gameId,
			String teamSide,
			String eventType,
			Integer eventOrder);

	List<LiveGameObjectEvent> findByGameIdOrderBySourceFrameTimestampUtcAscIdAsc(String gameId);

	List<LiveGameObjectEvent> findByGameIdAndSourceFrameTimestampUtcLessThanEqualOrderBySourceFrameTimestampUtcAscIdAsc(
			String gameId,
			LocalDateTime sourceFrameTimestampUtc);
}
