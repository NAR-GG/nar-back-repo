package com.toy.nar.domain.game.repository;

import com.toy.nar.app.analysis.dto.MultiCombinationFilterDto;
import com.toy.nar.domain.game.entity.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GameRepositoryCustom {

	Page<Game> findGamesByFilter(MultiCombinationFilterDto filter, Pageable pageable);
}
