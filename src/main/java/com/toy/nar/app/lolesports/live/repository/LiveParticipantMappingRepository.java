package com.toy.nar.app.lolesports.live.repository;

import com.toy.nar.app.lolesports.live.entity.LiveParticipantMapping;
import com.toy.nar.app.lolesports.live.entity.MappingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LiveParticipantMappingRepository extends JpaRepository<LiveParticipantMapping, Long> {

	Optional<LiveParticipantMapping> findByLiveGameIdAndLiveParticipantId(String liveGameId, Integer liveParticipantId);

	List<LiveParticipantMapping> findByLiveGameId(String liveGameId);

	List<LiveParticipantMapping> findByStatusIn(Collection<MappingStatus> statuses);
}
