-- 매치별 카드 진행도 워터마크와 매치 종료 발송 여부. 재기동해도 남아야 한다.
--
-- 둘 다 LiveActivityPushService 의 인메모리 필드였다.
--
--   lastProgressByMatch     뒤처진 이벤트를 걸러내는 워터마크
--   matchEndPushedMatchIds  매치 종료 카드를 이미 내보냈는지
--
-- 비면 무슨 일이 생기나. 업스트림이 이미 끝난 게임 id 를 계속 실어 보내면 디스커버리가 그 게임을
-- 다시 추적하고 세트 종료가 재발화한다(2026-07-31 Gen.G vs T1: 1세트 종료가 2세트 진행 중에 5회
-- 추가 발화). FCM 은 발송 이력 테이블이 막아 주지만 카드에는 그런 장치가 없어서, 2세트를 하는
-- 내내 카드가 "SET 1 종료" 로 덮이고, 더 나쁘게는 낡은 세트 종료가 그 시점 스코어로 매치 종료로
-- 판정돼 카드가 경기 도중 닫힌다.
--
-- 행은 매치당 하나다. 하루 수십 건이라 보존 정책을 두지 않는다 —
-- match_end_pushed_at 은 매치가 끝난 뒤에도 늦은 이벤트를 막아야 하므로 지울 수 없고,
-- live_activity_card_dispatch(회원×매치, 경기당 수천 행)와 달리 커지지 않는다.
CREATE TABLE live_activity_match_progress (
    match_id            VARCHAR(64) NOT NULL,
    -- 세트 번호와 국면을 하나로 접은 단조 증가 키(LiveActivityPushService.progressKey).
    progress_key        BIGINT      NOT NULL,
    -- 매치 종료 카드를 내보낸 시각. NULL 이면 아직 안 나갔다.
    match_end_pushed_at DATETIME    NULL,
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (match_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
