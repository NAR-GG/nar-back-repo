package com.toy.nar.participant.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.common.ChampionDataService;
import com.toy.nar.participant.dto.ChampionDto;
import com.toy.nar.participant.service.ChampionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/champions")
@RequiredArgsConstructor
public class ChampionController {

	private final ChampionService championService;
	private final ChampionDataService championDataService;

	@GetMapping
	public ResponseEntity<List<ChampionDto>> getAllChampions() {
		List<ChampionDto> champions = championService.getAllChampions();
		return ResponseEntity.ok(champions);
	}

	@PostMapping("/sync")
	public ResponseEntity<String> syncChampions() {
		championDataService.fetchAndSaveChampions();
		return ResponseEntity.ok("Champion data sync requested.");
	}

}
