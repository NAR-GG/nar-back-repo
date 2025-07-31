package com.toy.nar.domain.game.repository;

import com.toy.nar.app.analysis.dto.CombinationStatDto;
import com.toy.nar.domain.combination.strategy.MultiCombinationFilterDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface GameParticipantRepositoryCustom {

	Page<CombinationStatDto> findCombinationStats(
		List<String> championNames,
		MultiCombinationFilterDto filter,
		Pageable pageable
	);

	List<Long> findGameIdsByCombination(List<String> championNames, MultiCombinationFilterDto filter);

	Optional<CombinationStatDto> findSingleCombinationStat(List<String> championNames, MultiCombinationFilterDto filter);

}