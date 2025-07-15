package com.toy.nar.entity;

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
}
