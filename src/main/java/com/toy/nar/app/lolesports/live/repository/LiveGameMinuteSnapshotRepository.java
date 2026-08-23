package com.toy.nar.app.lolesports.live.repository;

import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LiveGameMinuteSnapshotRepository extends JpaRepository<LiveGameMinuteSnapshot, Long> {

	Optional<LiveGameMinuteSnapshot> findByGameIdAndMinuteBucketUtc(String gameId, LocalDateTime minuteBucketUtc);

	Optional<LiveGameMinuteSnapshot> findTopByGameIdOrderByMinuteBucketUtcDesc(String gameId);

	Optional<LiveGameMinuteSnapshot> findTopByGameIdOrderByMinuteBucketUtcAsc(String gameId);

	Optional<LiveGameMinuteSnapshot> findTopByGameIdOrderByFrameTimestampUtcDesc(String gameId);

	List<LiveGameMinuteSnapshot> findTop60ByGameIdOrderByMinuteBucketUtcDesc(String gameId);

	@Query("""
			SELECT s.gameId
			FROM LiveGameMinuteSnapshot s
			GROUP BY s.gameId
			ORDER BY MAX(s.frameTimestampUtc) DESC
			""")
	List<String> findRecentGameIds(Pageable pageable);

	/** 해당 매치에서 라이브 데이터를 수집한 게임 ID들(시작 시각 순). 게임 종료/재기동 후에도 영속된다. */
	@Query("""
			SELECT s.gameId
			FROM LiveGameMinuteSnapshot s
			WHERE s.matchId = :matchId
			GROUP BY s.gameId
			ORDER BY MIN(s.frameTimestampUtc)
			""")
	List<String> findGameIdsByMatchIdOrderByStart(String matchId);

	/**
	 * 해당 매치에서 {@code since} 이후로 프레임이 들어온 게임 ID들 = 지금 진행 중인 세트.
	 *
	 * <p>{@code LiveStateStore} 는 인메모리라 폴링이 도는 파드에만 채워진다. #442 로 스케줄러를
	 * 별도 파드로 뗀 뒤 웹 파드의 store 는 영구히 비어서, 인메모리만 보면 진행 중인 세트를
	 * ENDED 로 판정한다. DB 는 두 파드가 공유하므로 여기서 본다.</p>
	 *
	 * <p>{@code frameTimestampUtc} 는 이름 그대로 UTC 다. 호출부도 UTC 로 넘겨야 한다.</p>
	 */
	@Query("""
			SELECT s.gameId
			FROM LiveGameMinuteSnapshot s
			WHERE s.matchId = :matchId
			GROUP BY s.gameId
			HAVING MAX(s.frameTimestampUtc) > :since
			""")
	List<String> findFreshGameIdsByMatchId(String matchId, LocalDateTime since);
}
