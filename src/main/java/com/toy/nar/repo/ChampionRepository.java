package com.toy.nar.repo;

import com.toy.nar.entity.Champion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChampionRepository extends JpaRepository<Champion, Long> {

	/**
	 * 영문 이름으로 챔피언이 DB에 이미 존재하는지 확인합니다.
	 * ChampionDataService에서 중복 저장을 방지하기 위해 사용합니다.
	 * @param championNameEn 챔피언 영문 ID (예: "Aatrox")
	 * @return 존재 여부 (true/false)
	 */
	boolean existsByChampionNameEn(String championNameEn);

	/**
	 * 영문 이름으로 Champion 엔티티를 조회합니다.
	 * 나중에 CSV 데이터를 처리하는 DataIngestionService에서
	 * 챔피언 이름을 가지고 Champion 엔티티를 찾기 위해 사용됩니다.
	 * @param championNameEn 챔피언 영문 이름
	 * @return Optional<Champion>
	 */
	Optional<Champion> findByChampionNameEn(String championNameEn);
}
