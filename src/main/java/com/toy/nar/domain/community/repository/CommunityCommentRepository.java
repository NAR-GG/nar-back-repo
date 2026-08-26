package com.toy.nar.domain.community.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.toy.nar.domain.community.entity.CommunityComment;

public interface CommunityCommentRepository
		extends JpaRepository<CommunityComment, Long>, CommunityCommentRepositoryCustom {
}
