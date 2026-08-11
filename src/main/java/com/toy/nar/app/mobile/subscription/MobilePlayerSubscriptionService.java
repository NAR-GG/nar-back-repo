package com.toy.nar.app.mobile.subscription;

import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionPageResponse;
import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionResponse;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberFavoritePlayer;
import com.toy.nar.domain.member.repository.MemberFavoritePlayerRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.participant.PlayerRoleOrder;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobilePlayerSubscriptionService {

	private static final String LEAGUE_NAME = "LCK";
	private static final int CURRENT_SEASON_YEAR = 2026;

	private final MemberRepository memberRepository;
	private final MemberFavoritePlayerRepository subscriptionRepository;
	private final PlayerRepository playerRepository;

	public List<PlayerSubscriptionResponse> getSubscriptions(Long memberId) {
		requireMember(memberId);
		List<MemberFavoritePlayer> subscriptions = subscriptionRepository.findAllByMember_Id(memberId);
		if (subscriptions.isEmpty()) {
			return List.of();
		}

		Set<Long> playerIds = subscriptions.stream()
				.map(subscription -> subscription.getPlayer().getId())
				.collect(Collectors.toSet());
		Map<Long, PlayerRepository.LckPlayerOption> playerOptions = playerRepository
				.findLckPlayerOptionsByPlayerIds(LEAGUE_NAME, CURRENT_SEASON_YEAR, playerIds).stream()
				.collect(Collectors.toMap(
						PlayerRepository.LckPlayerOption::getPlayerId,
						option -> option,
						(existing, ignored) -> existing,
						LinkedHashMap::new));
		// LCK 옵션이 없는(은퇴/비출전) 구독 선수는 계정 기준 옵션으로 보완한다.
		playerRepository.findSoloRankPlayerOptionsByPlayerIds(playerIds)
				.forEach(option -> playerOptions.putIfAbsent(option.getPlayerId(), option));

		return subscriptions.stream()
				.map(subscription -> playerOptions.get(subscription.getPlayer().getId()))
				.filter(option -> option != null)
				.map(option -> PlayerSubscriptionResponse.from(option, true))
				.sorted(Comparator
						.comparingInt((PlayerSubscriptionResponse response) -> PlayerRoleOrder.of(response.role()))
						.thenComparing(PlayerSubscriptionResponse::playerName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public PlayerSubscriptionPageResponse getAvailablePlayers(
			Long memberId,
			String query,
			Long teamId,
			int page,
			int size) {
		requireMember(memberId);
		int safePage = Math.max(0, page);
		// 상한 300: 구독 가능 선수(2026 LCK 출전자 ∪ 솔랭 계정 보유자)가 100명을 넘어서
		// 기존 상한 100이면 로스터가 두 페이지로 쪼개졌다. 앱이 목록을 클라이언트에서
		// 정렬하는데 페이지가 나뉘면 뒤늦게 도착한 선수가 정렬 결과를 흔든다.
		// 온보딩(AuthController.ONBOARDING_PLAYER_PAGE)이 이미 200으로 같은 쿼리를 통째로 받는다.
		int safeSize = Math.max(1, Math.min(size, 300));
		String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
		Set<Long> subscribedPlayerIds = subscriptionRepository.findPlayerIdsByMemberId(memberId);
		Page<PlayerRepository.LckPlayerOption> players = playerRepository.findLckPlayerOptions(
				LEAGUE_NAME,
				CURRENT_SEASON_YEAR,
				teamId,
				normalizedQuery,
				PageRequest.of(safePage, safeSize));

		List<PlayerSubscriptionResponse> content = players.getContent().stream()
				.map(player -> PlayerSubscriptionResponse.from(
						player,
						subscribedPlayerIds.contains(player.getPlayerId())))
				.toList();
		return new PlayerSubscriptionPageResponse(
				content,
				players.getNumber(),
				players.getSize(),
				players.getTotalElements(),
				players.getTotalPages());
	}

	@Transactional
	public PlayerSubscriptionResponse subscribe(Long memberId, Long playerId) {
		Member member = requireMember(memberId);
		Player player = playerRepository.findById(playerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "선수를 찾을 수 없습니다."));
		PlayerRepository.LckPlayerOption playerOption = requireLckPlayer(playerId);

		subscriptionRepository.findByMember_IdAndPlayer_Id(memberId, playerId)
				.orElseGet(() -> subscriptionRepository.save(MemberFavoritePlayer.builder()
						.member(member)
						.player(player)
						.build()));
		return PlayerSubscriptionResponse.from(playerOption, true);
	}

	@Transactional
	public void delete(Long memberId, Long playerId) {
		requireMember(memberId);
		MemberFavoritePlayer subscription = subscriptionRepository
				.findByMember_IdAndPlayer_Id(memberId, playerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "선수 구독을 찾을 수 없습니다."));
		subscriptionRepository.delete(subscription);
	}

	private Member requireMember(Long memberId) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
	}

	private PlayerRepository.LckPlayerOption requireLckPlayer(Long playerId) {
		return playerRepository.findLckPlayerOption(LEAGUE_NAME, CURRENT_SEASON_YEAR, playerId).stream()
				.findFirst()
				.or(() -> playerRepository
						.findSoloRankPlayerOptionsByPlayerIds(Set.of(playerId)).stream()
						.findFirst())
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						"구독 가능한 선수가 아닙니다."));
	}
}
