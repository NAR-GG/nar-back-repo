package com.toy.nar.app.lolesports.live.repository;

import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteParticipantSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveGameMinuteParticipantSnapshotRepository extends JpaRepository<LiveGameMinuteParticipantSnapshot, Long> {

	void deleteBySnapshot_Id(Long snapshotId);

	List<LiveGameMinuteParticipantSnapshot> findBySnapshot_IdOrderByParticipantIdAsc(Long snapshotId);
}
