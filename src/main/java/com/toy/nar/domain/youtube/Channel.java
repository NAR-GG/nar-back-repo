package com.toy.nar.domain.youtube;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "channel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@ToString
public class Channel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "channel_id")
	private Long id;

	@Column(name = "youtube_channel_id", nullable = false, unique = true)
	private String youtubeChannelId;

	@Column(nullable = false)
	private String channelName;

	private String uploadPlaylistId;

	private String profileImageUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "channel_type")
	private ChannelType channelType;

	// 팀 공식 채널이면 그 팀의 코드(teams.team_code). LCK 공식·유튜버 채널은 null.
	@Column(name = "team_code", length = 10)
	private String teamCode;

	public void updateUploadPlaylistId(String uploadPlaylistId) {
		this.uploadPlaylistId = uploadPlaylistId;
	}
}
