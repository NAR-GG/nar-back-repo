package com.toy.nar.domain.community.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.toy.nar.domain.community.entity.CommunityPost;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long>, CommunityPostRepositoryCustom {
}
