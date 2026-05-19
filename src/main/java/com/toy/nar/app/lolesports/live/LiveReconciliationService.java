package com.toy.nar.app.lolesports.live;

import com.toy.nar.app.lolesports.live.entity.LiveGameMapping;
import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteParticipantSnapshot;
import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteSnapshot;
import com.toy.nar.app.lolesports.live.entity.LiveParticipantMapping;
import com.toy.nar.app.lolesports.live.repository.LiveGameMappingRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteParticipantSnapshotRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import com.toy.nar.app.lolesports.live.repository.LiveParticipantMappingRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatchGame;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.common.util.NameNormalizer;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveReconciliationService {

	private final LiveGameMinuteSnapshotRepository snapshotRepository;
	private final LiveGameMinuteParticipantSnapshotRepository participantSnapshotRepository;
	private final LiveGameMappingRepository gameMappingRepository;
	private final LiveParticipantMappingRepository participantMappingRepository;
	private final LeagueMatchRepository leagueMatchRepository;
	private final LeagueMatchGameRepository leagueMatchGameRepository;
	private final GameRepository gameRepository;
	private final GameParticipantRepository gameParticipantRepository;

	@Value("${lolesports.live.reconcile-game-limit:50}")
	private int reconcileGameLimit;

	@Value("${lolesports.live.reconcile-candidate-window-hours:24}")
	private long candidateWindowHours;

	@Value("${lolesports.live.reconcile-ambiguity-gap-minutes:15}")
	private long ambiguityGapMinutes;

	@Value("${lolesports.live.reconcile-max-game-gap-minutes:360}")
	private long maxGameGapMinutes;

	@Transactional
	public void reconcileRecentGames() {
		List<String> liveGameIds = snapshotRepository.findRecentGameIds(PageRequest.of(0, reconcileGameLimit));
		for (String liveGameId : liveGameIds) {
			reconcileSingleGame(liveGameId);
		}
	}

	@Transactional
	public void reconcileByGameId(String liveGameId) {
		if (liveGameId == null || liveGameId.isBlank()) {
			return;
		}
		reconcileSingleGame(liveGameId);
	}

	private void reconcileSingleGame(String liveGameId) {
		Optional<LiveGameMinuteSnapshot> latestOptional = snapshotRepository
				.findTopByGameIdOrderByFrameTimestampUtcDesc(liveGameId);
		if (latestOptional.isEmpty()) {
			return;
		}

		LiveGameMinuteSnapshot latest = latestOptional.get();
		LiveGameMinuteSnapshot first = snapshotRepository
				.findTopByGameIdOrderByMinuteBucketUtcAsc(liveGameId)
				.orElse(latest);

		LiveGameMapping gameMapping = gameMappingRepository.findByLiveGameId(liveGameId)
				.orElseGet(() -> new LiveGameMapping(liveGameId));

		String resolvedMatchId = resolveLiveMatchId(liveGameId, latest, gameMapping);
		Optional<LeagueMatch> resolvedLeagueMatch = resolvedMatchId == null || resolvedMatchId.isBlank()
				? Optional.empty()
				: leagueMatchRepository.findById(resolvedMatchId);
		gameMapping.updateLiveContext(
				resolvedMatchId,
				resolveLeagueName(latest, gameMapping, resolvedLeagueMatch),
				resolveTeamName(latest.getBlueTeamName(), gameMapping.getLiveBlueTeamName(), resolvedLeagueMatch.map(LeagueMatch::getBlueTeamName).orElse(null)),
				resolveTeamName(latest.getRedTeamName(), gameMapping.getLiveRedTeamName(), resolvedLeagueMatch.map(LeagueMatch::getRedTeamName).orElse(null)),
				first.getMinuteBucketUtc(),
				latest.getFrameTimestampUtc());

		reconcileGameMapping(gameMapping, latest, first);
		LiveGameMapping savedGameMapping = gameMappingRepository.save(gameMapping);

		reconcileParticipantMappings(savedGameMapping, latest);
	}

	private void reconcileGameMapping(
			LiveGameMapping mapping,
			LiveGameMinuteSnapshot latest,
			LiveGameMinuteSnapshot first) {
		String liveMatchId = mapping.getLiveMatchId();
		if (liveMatchId == null || liveMatchId.isBlank()) {
			mapping.markPending("live_match_id_missing");
			return;
		}

		Optional<LeagueMatch> leagueMatchOptional = leagueMatchRepository.findById(liveMatchId);
		if (leagueMatchOptional.isEmpty()) {
			mapping.markPending("league_match_not_found");
			return;
		}

		LeagueMatch leagueMatch = leagueMatchOptional.get();
		if (leagueMatch.getMatchDate() == null) {
			mapping.markPending("league_match_date_missing");
			return;
		}

		LocalDateTime referenceTime = first.getMinuteBucketUtc() != null
				? first.getMinuteBucketUtc()
				: leagueMatch.getMatchDate();
		LocalDateTime start = leagueMatch.getMatchDate().minusHours(candidateWindowHours);
		LocalDateTime end = leagueMatch.getMatchDate().plusHours(candidateWindowHours);

		List<Game> rawCandidates = gameRepository.findAllWithParticipantsByActualGameStartTimeBetween(start, end);
		List<Game> leagueCandidates = rawCandidates.stream()
				.filter(game -> sameLeague(game, mapping.getLiveLeagueName()))
				.toList();

		List<Game> teamCandidates = leagueCandidates.stream()
				.filter(game -> teamMatched(game, mapping.getLiveBlueTeamName(), mapping.getLiveRedTeamName()))
				.toList();

		if (teamCandidates.isEmpty()) {
			mapping.markPending("team_candidate_not_found");
			return;
		}

		List<Game> effectiveCandidates = teamCandidates;
		if (effectiveCandidates.isEmpty()) {
			mapping.markPending("no_internal_game_candidate");
			return;
		}

		List<GameDistance> sorted = effectiveCandidates.stream()
				.map(candidate -> new GameDistance(
						candidate,
						Math.abs(Duration.between(referenceTime, candidate.getActualGameStartTime()).toMinutes())))
				.sorted(Comparator.comparingLong(GameDistance::distanceMinutes))
				.toList();

		if (sorted.size() > 1) {
			long bestGap = sorted.get(0).distanceMinutes();
			long secondGap = sorted.get(1).distanceMinutes();
			if (Math.abs(secondGap - bestGap) <= ambiguityGapMinutes) {
				mapping.markAmbiguous("multiple_candidates_close_in_time");
				return;
			}
		}

		GameDistance best = sorted.get(0);
		if (best.distanceMinutes() > maxGameGapMinutes) {
			mapping.markPending("candidate_time_gap_too_large");
			return;
		}

		double confidence = confidenceByTimeGap(best.distanceMinutes(), !teamCandidates.isEmpty());
		String method = !teamCandidates.isEmpty()
				? "LEAGUE_TEAM_TIME_NEAREST"
				: "LEAGUE_TIME_NEAREST";
		String reason = "candidate_gap_minutes=" + best.distanceMinutes();

		mapping.markMapped(best.game().getId(), confidence, method, reason);
	}

	private String resolveLiveMatchId(
			String liveGameId,
			LiveGameMinuteSnapshot latestSnapshot,
			LiveGameMapping existingMapping) {
		if (latestSnapshot.getMatchId() != null && !latestSnapshot.getMatchId().isBlank()) {
			return latestSnapshot.getMatchId();
		}
		if (existingMapping.getLiveMatchId() != null && !existingMapping.getLiveMatchId().isBlank()) {
			return existingMapping.getLiveMatchId();
		}
		return leagueMatchGameRepository.findWithMatchByGameId(liveGameId)
				.map(LeagueMatchGame::getLeagueMatch)
				.map(LeagueMatch::getId)
				.orElse(null);
	}

	private String firstNonBlank(String primary, String secondary) {
		if (primary != null && !primary.isBlank()) {
			return primary;
		}
		return secondary;
	}

	private String resolveLeagueName(
			LiveGameMinuteSnapshot latestSnapshot,
			LiveGameMapping mapping,
			Optional<LeagueMatch> leagueMatch) {
		if (leagueMatch.isPresent() && leagueMatch.get().getLeagueName() != null && !leagueMatch.get().getLeagueName().isBlank()) {
			return leagueMatch.get().getLeagueName();
		}
		return firstNonBlank(latestSnapshot.getLeagueName(), mapping.getLiveLeagueName());
	}

	private String resolveTeamName(String latest, String mapped, String fromLeagueMatch) {
		if (fromLeagueMatch != null && !fromLeagueMatch.isBlank()) {
			return fromLeagueMatch;
		}
		if (isLikelyEsportsTeamId(latest)) {
			return firstNonBlank(mapped, latest);
		}
		return firstNonBlank(latest, mapped);
	}

	private boolean isLikelyEsportsTeamId(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		return value.matches("\\d{12,}");
	}

	private void reconcileParticipantMappings(LiveGameMapping gameMapping, LiveGameMinuteSnapshot latestSnapshot) {
		List<LiveGameMinuteParticipantSnapshot> liveParticipants = participantSnapshotRepository
				.findBySnapshot_IdOrderByParticipantIdAsc(latestSnapshot.getId());
		if (liveParticipants.isEmpty()) {
			return;
		}

		List<GameParticipant> internalParticipants = List.of();
		if (gameMapping.getInternalGameId() != null) {
			internalParticipants = gameParticipantRepository.findWithDetailsByGameIds(List.of(gameMapping.getInternalGameId()));
		}

		for (LiveGameMinuteParticipantSnapshot liveParticipant : liveParticipants) {
			LiveParticipantMapping participantMapping = participantMappingRepository
					.findByLiveGameIdAndLiveParticipantId(
							liveParticipant.getSnapshot().getGameId(),
							liveParticipant.getParticipantId())
					.orElseGet(() -> new LiveParticipantMapping(
							liveParticipant.getSnapshot().getGameId(),
							liveParticipant.getParticipantId()));

			participantMapping.updateLiveContext(
					liveParticipant.getTeamSide(),
					liveParticipant.getRole(),
					liveParticipant.getPlayerName(),
					liveParticipant.getEsportsPlayerId(),
					liveParticipant.getChampionName());

			if (gameMapping.getInternalGameId() == null) {
				participantMapping.markPending("game_not_mapped");
				participantMappingRepository.save(participantMapping);
				continue;
			}

			Optional<ParticipantMatchResult> matched = findParticipantMatch(liveParticipant, internalParticipants);
			if (matched.isPresent()) {
				ParticipantMatchResult matchResult = matched.get();
				GameParticipant gameParticipant = matchResult.gameParticipant();
				participantMapping.markMapped(
						gameParticipant.getId(),
						gameParticipant.getGame().getId(),
						gameParticipant.getPlayer().getId(),
						gameParticipant.getTeam().getId(),
						gameParticipant.getChampion().getId(),
						matchResult.confidence(),
						matchResult.method(),
						matchResult.reason());
			} else {
				participantMapping.markPending("participant_candidate_not_found");
			}
			participantMappingRepository.save(participantMapping);
		}
	}

	private Optional<ParticipantMatchResult> findParticipantMatch(
			LiveGameMinuteParticipantSnapshot liveParticipant,
			List<GameParticipant> internalParticipants) {
		List<GameParticipant> sideCandidates = internalParticipants.stream()
				.filter(candidate -> sameSide(candidate.getSide(), liveParticipant.getTeamSide()))
				.toList();
		if (sideCandidates.isEmpty()) {
			return Optional.empty();
		}

		Set<String> nameCandidates = toLiveNameCandidates(liveParticipant.getPlayerName());
		List<GameParticipant> nameMatched = sideCandidates.stream()
				.filter(candidate -> nameCandidates.contains(normalizeText(candidate.getPlayer().getName())))
				.toList();

		if (nameMatched.size() == 1) {
			GameParticipant chosen = nameMatched.get(0);
			if (!sameChampion(liveParticipant.getChampionName(), chosen.getChampion().getChampionNameEn())) {
				return Optional.empty();
			}
			return Optional.of(new ParticipantMatchResult(
					chosen,
					0.98d,
					"PLAYER_NAME_SIDE",
					"player_name + side matched"));
		}
		if (nameMatched.size() > 1) {
			return Optional.empty();
		}

		String liveRole = normalizeRole(liveParticipant.getRole());
		if (liveRole != null) {
			List<GameParticipant> roleMatched = sideCandidates.stream()
					.filter(candidate -> liveRole.equals(normalizeRole(candidate.getPosition())))
					.toList();
			if (roleMatched.size() == 1) {
				GameParticipant chosen = roleMatched.get(0);
				if (!sameChampion(liveParticipant.getChampionName(), chosen.getChampion().getChampionNameEn())) {
					return Optional.empty();
				}
				return Optional.of(new ParticipantMatchResult(
						chosen,
						0.75d,
						"ROLE_SIDE_CHAMPION",
						"role + side + champion matched"));
			}
		}

		return Optional.empty();
	}

	private boolean sameLeague(Game game, String liveLeagueName) {
		if (game == null || game.getLeague() == null) {
			return false;
		}
		if (liveLeagueName == null || liveLeagueName.isBlank()) {
			return true;
		}
		return liveLeagueName.trim().equalsIgnoreCase(game.getLeague().getLeagueName());
	}

	private boolean teamMatched(Game game, String liveBlue, String liveRed) {
		if (liveBlue == null || liveRed == null) {
			return true;
		}

		String internalBlue = teamNameFromGame(game, "Blue");
		String internalRed = teamNameFromGame(game, "Red");
		if (internalBlue == null || internalRed == null) {
			return false;
		}

		String normLiveBlue = NameNormalizer.normalizeTeamName(liveBlue);
		String normLiveRed = NameNormalizer.normalizeTeamName(liveRed);
		String normInternalBlue = NameNormalizer.normalizeTeamName(internalBlue);
		String normInternalRed = NameNormalizer.normalizeTeamName(internalRed);

		boolean sameSide = normLiveBlue.equalsIgnoreCase(normInternalBlue)
				&& normLiveRed.equalsIgnoreCase(normInternalRed);
		boolean swappedSide = normLiveBlue.equalsIgnoreCase(normInternalRed)
				&& normLiveRed.equalsIgnoreCase(normInternalBlue);
		return sameSide || swappedSide;
	}

	private String teamNameFromGame(Game game, String side) {
		return game.getParticipants().stream()
				.filter(participant -> sameSide(participant.getSide(), side))
				.map(participant -> participant.getTeam().getName())
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	private boolean sameSide(String left, String right) {
		if (left == null || right == null) {
			return false;
		}
		return normalizeText(left).equals(normalizeText(right));
	}

	private Set<String> toLiveNameCandidates(String livePlayerName) {
		if (livePlayerName == null || livePlayerName.isBlank()) {
			return Set.of();
		}
		String trimmed = livePlayerName.trim();
		List<String> candidates = new ArrayList<>();
		candidates.add(normalizeText(trimmed));

		String[] parts = trimmed.split("\\s+");
		if (parts.length >= 2) {
			candidates.add(normalizeText(String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length))));
			candidates.add(normalizeText(parts[parts.length - 1]));
		}

		return candidates.stream()
				.filter(value -> value != null && !value.isBlank())
				.collect(Collectors.toSet());
	}

	private boolean sameChampion(String liveChampionName, String internalChampionName) {
		String left = NameNormalizer.normalizeChampionName(liveChampionName);
		String right = NameNormalizer.normalizeChampionName(internalChampionName);
		return !left.isBlank() && left.equalsIgnoreCase(right);
	}

	private String normalizeRole(String role) {
		if (role == null || role.isBlank()) {
			return null;
		}
		String normalized = role.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "top" -> "top";
			case "jungle", "jungler", "jng" -> "jng";
			case "middle", "mid" -> "mid";
			case "bottom", "bot", "adc" -> "bot";
			case "support", "sup" -> "sup";
			default -> normalized;
		};
	}

	private String normalizeText(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private double confidenceByTimeGap(long minutes, boolean teamMatched) {
		if (teamMatched) {
			if (minutes <= 30) {
				return 0.95d;
			}
			if (minutes <= 120) {
				return 0.88d;
			}
			return 0.8d;
		}
		if (minutes <= 30) {
			return 0.82d;
		}
		if (minutes <= 120) {
			return 0.74d;
		}
		return 0.65d;
	}

	private record GameDistance(Game game, long distanceMinutes) {
	}

	private record ParticipantMatchResult(GameParticipant gameParticipant, double confidence, String method,
			String reason) {
	}
}
