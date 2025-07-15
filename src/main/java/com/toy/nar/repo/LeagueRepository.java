package com.toy.nar.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.toy.nar.entity.League;

public interface LeagueRepository extends JpaRepository<League, Long> {
	/**
	 * 리그의 비즈니스 키(이름, 연도, 스플릿, 플레이오프 여부)로 League 엔티티를 조회합니다.
	 * 중복 리그 생성을 방지합니다.
	 */
	Optional<League> findByLeagueNameAndSeasonYearAndSeasonSplitAndIsPlayoffs(
		String leagueName, Integer seasonYear, String seasonSplit, Boolean isPlayoffs
	);
}