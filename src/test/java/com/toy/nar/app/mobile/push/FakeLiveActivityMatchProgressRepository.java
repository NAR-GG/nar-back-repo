package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.LiveActivityMatchProgressRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 진행도 테이블의 인메모리 대역. {@code GREATEST} 와 {@code IS NULL} 선점을 그대로 흉내낸다.
 *
 * <p>"재기동" 은 서비스 인스턴스를 새로 만들면서 이 대역을 그대로 넘기는 것으로 흉내낸다 —
 * 새 인스턴스의 인메모리 맵은 비어 있고 이 대역만 남으므로, DB 에서 이어받는지가 드러난다.</p>
 */
class FakeLiveActivityMatchProgressRepository implements LiveActivityMatchProgressRepository {

	private final Map<String, Long> progress = new LinkedHashMap<>();
	private final Map<String, Boolean> matchEnded = new LinkedHashMap<>();

	/** DB 장애를 흉내낸다. 조회·저장이 실패해도 발송은 계속돼야 한다. */
	private boolean fail;

	void fail() {
		this.fail = true;
	}

	@Override
	public Optional<Long> findProgressKey(String matchId) {
		if (fail) {
			throw new RuntimeException("db down");
		}
		return Optional.ofNullable(progress.get(matchId));
	}

	@Override
	public void raiseProgressKey(String matchId, long progressKey) {
		if (fail) {
			throw new RuntimeException("db down");
		}
		progress.merge(matchId, progressKey, Math::max);
	}

	@Override
	public boolean isMatchEndPushed(String matchId) {
		if (fail) {
			throw new RuntimeException("db down");
		}
		return Boolean.TRUE.equals(matchEnded.get(matchId));
	}

	@Override
	public boolean claimMatchEndPush(String matchId) {
		if (fail) {
			throw new RuntimeException("db down");
		}
		return matchEnded.putIfAbsent(matchId, Boolean.TRUE) == null;
	}

	Long storedProgress(String matchId) {
		return progress.get(matchId);
	}
}
