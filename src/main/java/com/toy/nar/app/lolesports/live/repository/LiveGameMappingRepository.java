package com.toy.nar.app.lolesports.live.repository;

import com.toy.nar.app.lolesports.live.entity.LiveGameMapping;
import com.toy.nar.app.lolesports.live.entity.MappingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LiveGameMappingRepository extends JpaRepository<LiveGameMapping, Long> {

	Optional<LiveGameMapping> findByLiveGameId(String liveGameId);

	List<LiveGameMapping> findByStatusIn(Collection<MappingStatus> statuses);
}
