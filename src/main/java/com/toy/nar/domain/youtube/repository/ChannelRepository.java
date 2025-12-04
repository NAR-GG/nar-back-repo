package com.toy.nar.domain.youtube.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.toy.nar.domain.youtube.Channel;

public interface ChannelRepository extends JpaRepository<Channel, String> {
	boolean existsById(String id);
	List<Channel> findByYoutubeChannelIdIn(List<String> youtubeChannelIds);

	Optional<Channel> findByYoutubeChannelId(String channelId);
}
