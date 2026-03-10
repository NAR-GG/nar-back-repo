package com.toy.nar.domain.game.repository;

import com.toy.nar.domain.game.entity.GameExternalIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GameExternalIdentityRepository extends JpaRepository<GameExternalIdentity, Long> {

    Optional<GameExternalIdentity> findBySourceAndExternalGameId(String source, String externalGameId);

    List<GameExternalIdentity> findBySourceAndExternalGameIdIn(String source, Collection<String> externalGameIds);

    List<GameExternalIdentity> findByGame_Id(Long gameId);

    List<GameExternalIdentity> findBySourceAndGame_IdIn(String source, Collection<Long> gameIds);
}
