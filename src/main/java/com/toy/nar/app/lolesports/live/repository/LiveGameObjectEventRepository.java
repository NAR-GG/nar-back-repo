package com.toy.nar.app.lolesports.live.repository;

import com.toy.nar.app.lolesports.live.entity.LiveGameObjectEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LiveGameObjectEventRepository extends JpaRepository<LiveGameObjectEvent, Long> {

	boolean existsByGameIdAndTeamSideAndEventTypeAndEventOrder(
			String gameId,
			String teamSide,
			String eventType,
			Integer eventOrder);

	/**
	 * 이미 저장된 이벤트를 집어 온다. 저장 여부와 무관하게 FCM 푸시를 재시도해야 하므로
	 * exists 판정만으로는 부족하고(푸시 멱등 키가 이 행의 id 다) 행 자체가 필요하다.
	 */
	Optional<LiveGameObjectEvent> findByGameIdAndTeamSideAndEventTypeAndEventOrder(
			String gameId,
			String teamSide,
			String eventType,
			Integer eventOrder);

	List<LiveGameObjectEvent> findByGameIdOrderBySourceFrameTimestampUtcAscIdAsc(String gameId);

	List<LiveGameObjectEvent> findByGameIdAndSourceFrameTimestampUtcLessThanEqualOrderBySourceFrameTimestampUtcAscIdAsc(
			String gameId,
			LocalDateTime sourceFrameTimestampUtc);
}
