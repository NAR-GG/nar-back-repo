package com.toy.nar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.util.Objects;

@Entity
@Table(name = "leagues", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"league_name", "season_year", "season_split", "is_playoffs"})
}) // 복합 유니크 제약 조건 추가
@Getter // Lombok: Getter 자동 생성
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Lombok: 기본 생성자
@EqualsAndHashCode(of = {"leagueName", "seasonYear", "seasonSplit", "isPlayoffs"}) // Lombok: 동등성 비교 (비즈니스 키)
@ToString // Lombok: toString 메서드
public class League {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "league_id")
	private Long id;

	@Column(name = "league_name", nullable = false, length = 50)
	private String leagueName;

	@Column(name = "season_year", nullable = false)
	private Integer seasonYear;

	@Column(name = "season_split", nullable = false, length = 20)
	private String seasonSplit;

	@Column(name = "is_playoffs", nullable = false)
	private Boolean isPlayoffs; // CSV 'playoffs' (0/1)을 Boolean으로 변환하여 저장

	@Builder // Lombok: 빌더 패턴 생성자
	public League(String leagueName, Integer seasonYear, String seasonSplit, Boolean isPlayoffs) {
		this.leagueName = Objects.requireNonNull(leagueName, "League name must not be null");
		this.seasonYear = Objects.requireNonNull(seasonYear, "Season year must not be null");
		this.seasonSplit = Objects.requireNonNull(seasonSplit, "Season split must not be null");
		this.isPlayoffs = Objects.requireNonNull(isPlayoffs, "Is playoffs must not be null");
	}
}