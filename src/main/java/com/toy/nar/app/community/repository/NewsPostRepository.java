package com.toy.nar.app.community.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {
	Optional<NewsPost> findByPostUrl(String postUrl);
	
	List<NewsPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

	void deleteByCreatedAtBefore(LocalDateTime dateTime);
}