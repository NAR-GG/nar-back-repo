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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
				.map(subscription -> {
					PlayerRepository.LckPlayerOption option =
							playerOptions.get(subscription.getPlayer().getId());
					return option == null ? null : PlayerSubscriptionResponse.from(
							option, true, subscription.isStartEnabled(), subscription.isEndEnabled());
				})
				.filter(response -> response != null)
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
	 * (실측 2026-08-15 10:58:19, member 5711 / player 80 이 600ms 안에 4연타 — 1213 한 번,
	 * 1062 세 번). 500 을 받은 클라이언트가 재시도하면서 경합을 더 키우는 자기증폭 루프였다.</p>
	 *
	 * <p>중복(1062)은 upsert 한 문장으로 없앴다. 판정은 유니크 제약이 하고 앱은 결과만 받는다.</p>
	 *
	 * <p>deadlock(1213)은 문법으로는 못 없앤다. 3세션 동시 INSERT 재현에서
	 * {@code INSERT}/{@code INSERT IGNORE}/{@code ON DUPLICATE KEY UPDATE} 가 <b>똑같이</b>
	 * 1213 을 냈다 — 락 순환은 중복을 어떻게 처리하느냐가 아니라 InnoDB 가 유니크 인덱스에
	 * gap 락을 잡는 방식에서 나오기 때문이다. 그래서 예방이 아니라 재시도로 받는다.
	 * MySQL 이 에러 메시지에 직접 그렇게 답한다 — {@code try restarting transaction}.</p>
	 *
	 * <p>{@code NOT_SUPPORTED} 인 이유: 재시도하려면 실패한 트랜잭션이 이미 닫혀 있어야 한다.
	 * 쓰기를 {@code insertIfAbsent} 자신의 트랜잭션에 가둬야 여기서 잡고 다시 부를 수 있다.
	 * 이 메서드까지 트랜잭션으로 감싸면 첫 실패가 그 트랜잭션을 rollback-only 로 만들어
	 * 재시도가 무의미해진다. 앞선 조회들은 서로 독립적인 검증이라 한 트랜잭션에 묶을 이유가 없다.</p>
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public PlayerSubscriptionResponse subscribe(Long memberId, Long playerId) {
		requireMember(memberId);
		playerRepository.findById(playerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "선수를 찾을 수 없습니다."));
		PlayerRepository.LckPlayerOption playerOption = requireLckPlayer(playerId);

		insertWithDeadlockRetry(memberId, playerId);
		return PlayerSubscriptionResponse.from(playerOption, true);
	}

	/**
	 * deadlock 은 재시도가 정답인 에러다. 희생자로 뽑힌 쪽은 롤백돼 있으므로 그냥 다시 넣으면 된다 —
	 * 상대가 이미 커밋했으면 이번엔 중복이라 조용히 0행이고, 상대도 롤백했으면 이번엔 들어간다.
	 *
	 * <p>재시도는 한 번뿐이다. 두 번 연속 락 순환에 걸리는 건 이 테이블의 정상 부하가 아니라
	 * 다른 사고이므로 삼키지 않고 올린다.</p>
	 */
	private void insertWithDeadlockRetry(Long memberId, Long playerId) {
		try {
			subscriptionRepository.insertIfAbsent(memberId, playerId, LocalDateTime.now());
		}
		catch (CannotAcquireLockException e) {
			subscriptionRepository.insertIfAbsent(memberId, playerId, LocalDateTime.now());
		}
	}

	/** 구독을 유지한 채 시작/종료 알림 토글만 바꾼다. null 인 값은 기존 값을 유지한다. */
	@Transactional
	public void updateToggles(Long memberId, Long playerId, Boolean startEnabled, Boolean endEnabled) {
		requireMember(memberId);
		MemberFavoritePlayer subscription = subscriptionRepository
				.findByMember_IdAndPlayer_Id(memberId, playerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "선수 구독을 찾을 수 없습니다."));
		subscription.updateToggles(startEnabled, endEnabled);
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
