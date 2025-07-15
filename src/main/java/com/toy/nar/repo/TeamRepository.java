package com.toy.nar.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.toy.nar.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
	Optional<Team> findByName(String name);
}