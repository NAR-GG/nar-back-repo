package com.toy.nar.app.mobile.solorank;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 월즈 솔랭 공용판 캐시. {@code MobileScheduleCacheableService} 와 같은 이유로 트랜잭션 없는 별도 빈이다 —
 * {@link MobileSoloRankStatusService} 는 클래스에 {@code @Transactional(readOnly = true)} 가 걸려 있어
 * 그 안에 캐시를 두면 히트에도 커넥션을 먼저 잡을 수 있다.
 *
 * <p>실측 2026-10-24 08:24 LOS vs T1 세트 시작: 이 엔드포인트가 평시에도 커넥션을 121ms(p50)씩 쥐는데
 * 버스트 1분에 535건이 몰려 Hikari 30 을 채웠다(3초 넘은 요청 280건 중 106건). 명단·라이브·끝난 판·
 * 응원 합계는 회원과 무관하니 한 벌만 세고, 내 구독·내 응원 수만 요청마다 덧입힌다.
 */
@Service
@RequiredArgsConstructor
public class MobileSoloRankCacheableService {

	private final MobileSoloRankStatusService statusService;

	/** TTL 10초. 응원 합계는 앱이 내 낙관적 수와 max 로 합쳐 보여서 몇 초 낡아도 숫자가 줄지 않는다. */
	@Cacheable(value = "worldsSoloRankBoard", key = "'all'", sync = true)
	public MobileSoloRankStatusService.WorldsBoard worldsBoard() {
		return statusService.worldsBoard();
	}
}
