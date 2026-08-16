-- 알림 세분화: 라이브 이벤트 5종 분리 + 솔랭 종료 알림
--
-- 라이브 이벤트가 라이브 푸시의 81%(7일 258,525건)이고 세트당 49건인데, 그중 킬 29.7건과
-- 타워 11.7건이 84%다. live_event_enabled 하나로는 "바론만 받고 킬은 안 받기"가 불가능해
-- 종류별 토글을 만든다. live_event_enabled 는 마스터 스위치로 남고 조건은 AND 로 걸린다.
--
-- 기존 구독은 전부 TRUE 로 채운다(현상 유지). 앱의 토글 UI 가 나중에 배포되므로 FALSE 로
-- 백필하면 사용자가 되돌릴 수단이 없는 상태에서 알림이 조용히 줄어든다.

ALTER TABLE member_match_subscription
    ADD COLUMN kill_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN baron_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN dragon_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN tower_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN inhibitor_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE member_team_notification_subscription
    ADD COLUMN kill_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN baron_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN dragon_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN tower_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN inhibitor_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 솔랭 시작/종료 토글. 기존 24,431 구독은 시작만 켜진 현재 동작을 유지하고,
-- 종료는 끄고 시작한다 — 선수당 하루 여러 판이라 켜면 알림이 두 배가 된다.
ALTER TABLE member_favorite_player
    ADD COLUMN start_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN end_enabled   BOOLEAN NOT NULL DEFAULT FALSE;

-- 종료 처리를 끝낸 게임 표시. 이게 없으면 이미 알림을 보낸 게임을 폴 주기마다
-- match-v5 로 다시 조회해 Riot 쿼터가 샌다.
ALTER TABLE player_solo_rank_game
    ADD COLUMN end_notified_at DATETIME NULL;

-- 시작/종료 멱등키 분리. 기존 키가 (member_id, player_id, game_id) 라 시작 알림을 보낸
-- 게임은 종료 알림이 중복으로 걸려 막힌다.
ALTER TABLE player_solo_rank_push_delivery
    ADD COLUMN event_type VARCHAR(16) NOT NULL DEFAULT 'START';

ALTER TABLE player_solo_rank_push_delivery
    DROP INDEX uk_player_solo_rank_push_delivery,
    ADD UNIQUE KEY uk_player_solo_rank_push_delivery (member_id, player_id, game_id, event_type);
