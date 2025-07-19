package com.toy.nar.domain.participant.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.Objects;

@Entity
@Table(name = "champions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@ToString
public class Champion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "champion_id")
	private Long id;

	@Column(name = "champion_name_kr", nullable = false, length = 50)
	private String championNameKr;

	@Column(name = "champion_name_en", nullable = false, unique = true, length = 50)
	private String championNameEn;

	@Column(name = "image_url", nullable = false)
	private String imageUrl;

	@Builder
	public Champion(String championNameKr, String championNameEn, String imageUrl) {
		this.championNameKr = Objects.requireNonNull(championNameKr);
		this.championNameEn = Objects.requireNonNull(championNameEn);
		this.imageUrl = Objects.requireNonNull(imageUrl);
	}

	public void updateImageUrl(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			throw new IllegalArgumentException("Image URL must not be null or blank");
		}
		this.imageUrl = imageUrl;
	}
}
