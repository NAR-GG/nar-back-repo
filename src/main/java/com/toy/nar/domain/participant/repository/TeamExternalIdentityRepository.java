package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.TeamExternalIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamExternalIdentityRepository extends JpaRepository<TeamExternalIdentity, Long> {

    @Query("""
            SELECT tei
            FROM TeamExternalIdentity tei
            JOIN FETCH tei.team t
            WHERE tei.source = :source
              AND tei.externalTeamId = :externalTeamId
            """)
    Optional<TeamExternalIdentity> findBySourceAndExternalTeamId(@Param("source") String source,
            @Param("externalTeamId") String externalTeamId);

    @Query("""
            SELECT tei
            FROM TeamExternalIdentity tei
            JOIN FETCH tei.team t
            WHERE tei.source = :source
              AND tei.externalTeamId IN :externalTeamIds
            """)
    List<TeamExternalIdentity> findBySourceAndExternalTeamIdIn(@Param("source") String source,
            @Param("externalTeamIds") Collection<String> externalTeamIds);

    List<TeamExternalIdentity> findByTeam_Id(Long teamId);
}
