package com.toy.nar.domain.member.repository;

import java.util.Optional;

/**
 * 매치별 카드 진행도 워터마크와 매치 종료 발송 여부. 재기동을 견디는 부분만 담는다.
 *
 * <p>원자성은 이 리포지토리가 아니라 호출부의 인메모리 맵이 준다 — 스케줄러 파드는 항상 하나이고
 * 같은 매치의 이벤트가 동시에 들어올 수 있어 판정은 JVM 안에서 직렬화하는 편이 싸다. 여기서는
 * 그 판정 결과를 <b>순서와 무관하게</b> 저장하기만 한다({@code GREATEST}).</p>
 */
public interface LiveActivityMatchProgressRepository {

	/** 저장된 워터마크. 없으면 empty — 이 매치를 아직 한 번도 갱신하지 않았다는 뜻이다. */
	Optional<Long> findProgressKey(String matchId);

	/**
	 * 워터마크를 올린다. 이미 더 큰 값이 있으면 그대로 둔다.
	 *
	 * <p>{@code GREATEST} 로 쓰는 이유 — 판정은 인메모리에서 직렬화되지만 DB 쓰기는 그 밖에서
	 * 일어나 순서가 뒤집힐 수 있다. 최댓값을 취하면 순서에 의존하지 않는다.</p>
	 */
	void raiseProgressKey(String matchId, long progressKey);

	/** 이 매치의 종료 카드가 이미 나갔는지. */
	boolean isMatchEndPushed(String matchId);

	/**
	 * 종료 발송을 선점한다. 처음 선점했으면 true.
	 *
	 * <p>발송자가 셋(프레임 편승·복구 재시도·스윕)이라 "종료가 나간 뒤 늦은 setEnded 가 카드를
	 * 되돌리는" 역행을 막으려면 이 판정이 발송 지점에 있어야 한다.</p>
	 */
	boolean claimMatchEndPush(String matchId);
}
