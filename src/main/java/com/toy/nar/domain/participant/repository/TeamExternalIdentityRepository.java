package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.TeamExternalIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamExternalIdentityRepository extends JpaRepository<TeamExternalIdentity, Long> {

    Optional<TeamExternalIdentity> findBySourceAndExternalTeamId(String source, String externalTeamId);

    List<TeamExternalIdentity> findBySourceAndExternalTeamIdIn(String source, Collection<String> externalTeamIds);

    List<TeamExternalIdentity> findByTeam_Id(Long teamId);
}
