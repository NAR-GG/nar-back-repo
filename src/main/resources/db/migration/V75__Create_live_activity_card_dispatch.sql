-- 잠금화면 카드(push-to-start)를 회원·매치당 한 번만 만들기 위한 발행 이력.
--
-- 지금까지 중복 방지는 두 장치뿐이었다.
--   1) findStartTargets 의 NOT EXISTS(active LiveActivityToken)  — 그 토큰은 앱이 실행돼야 올라온다
--   2) LiveActivityPushService.recentStarts (Caffeine 30초)      — JVM 인메모리
-- 둘 다 새는 자리가 있었다. 앱이 실행되지 않은 기기는 (1)에 안 걸려 세트마다 카드가 새로
-- 만들어졌고(실측 2026-08-23 T1 vs HLE: 세트1 3,120건 → 세트2 2,702건, 약 2,700명이 카드 2장),
-- (2)는 #442 로 파드가 둘이 된 뒤 세트 시작(스케줄러)과 구독 직후 따라잡기(웹)가 서로 다른
-- JVM 이라 창을 공유하지 못한다.
--
-- DB 는 두 파드가 공유하고 재기동에도 남으므로 여기서 막는다. UNIQUE 를 (member_id, match_id)
-- 로 잡아 "매치당 한 장"이 된다 — 세트마다 재발행하지 않는다.
--
-- set_number 는 진단용이다(어느 세트에서 발행됐는지). 키에는 넣지 않는다.
CREATE TABLE live_activity_card_dispatch (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    match_id   VARCHAR(64) NOT NULL,
    set_number INT         NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 컬럼 순서가 (match_id, member_id) 인 이유 — 이 하나로 세 쿼리를 다 덮는다.
    --   선점 조회  WHERE match_id = ? AND member_id IN (...)   선두 컬럼부터 맞는다
    --   해제       WHERE member_id = ? AND match_id = ?        전체 키가 맞는다
    --   매치 정리  WHERE match_id = ?                          선두 컬럼 prefix
    -- (member_id, match_id) 로 두면 선점 조회가 선두 컬럼을 못 써서 보조 인덱스를 하나 더
    -- 만들어야 한다. 유일성 제약은 어느 순서든 같으므로 인덱스가 적은 쪽을 고른다.
    UNIQUE KEY uk_live_activity_card_dispatch (match_id, member_id),
    CONSTRAINT fk_live_activity_card_dispatch_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
