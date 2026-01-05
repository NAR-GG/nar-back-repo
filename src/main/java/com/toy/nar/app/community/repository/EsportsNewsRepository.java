package com.toy.nar.app.community.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EsportsNewsRepository extends JpaRepository<EsportsNews, Long> {
	Optional<EsportsNews> findByPostUrl(String postUrl);
	
	List<EsportsNews> findAllByOrderByCreatedAtDesc(Pageable pageable);

	void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
