package com.toy.nar.app.mobile.subscription;

import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionPageResponse;
import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionResponse;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberFavoritePlayer;
import com.toy.nar.domain.member.repository.MemberFavoritePlayerRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.participant.PlayerRoleOrder;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
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

	/**
	 * 선수를 구독한다. 몇 번을 호출하든 결과가 같다.
	 *
	 * <p>예전엔 {@code findByMember_IdAndPlayer_Id().orElseGet(save)} 였다. 이 SELECT 는 락을
	 * 잡지 않으므로 같은 (member, player) 요청이 동시에 들어오면 전부 "구독 없음" 으로 보고
	 * 다 같이 INSERT 했고, 유니크 인덱스 {@code uq_member_favorite_player} 에서
	 * duplicate(1062) 또는 deadlock(1213) 이 나 500 으로 떨어졌다
	 * (실측 2026-08-15 10:58:19, member 5711 / player 80 이 600ms 안에 4연타).
	 * 그리고 500 을 받은 클라이언트가 재시도하면서 경합을 더 키우는 자기증폭 루프였다.</p>
	 *
	 * <p>지금은 INSERT IGNORE 한 문장으로 끝낸다. 중복은 예외가 아니라 무시되고, 쓰기가 단일
	 * statement 라 앞선 조회들과 INSERT 사이의 경합 창도 사라진다.</p>
	 */
	@Transactional
	public PlayerSubscriptionResponse subscribe(Long memberId, Long playerId) {
		requireMember(memberId);
		playerRepository.findById(playerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "선수를 찾을 수 없습니다."));
		PlayerRepository.LckPlayerOption playerOption = requireLckPlayer(playerId);

		subscriptionRepository.insertIgnore(memberId, playerId, LocalDateTime.now());
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
