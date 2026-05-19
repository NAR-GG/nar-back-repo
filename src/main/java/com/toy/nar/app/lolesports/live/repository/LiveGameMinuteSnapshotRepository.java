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
}
