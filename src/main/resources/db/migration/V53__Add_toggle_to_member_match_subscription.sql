-- 경기 예약 구독에 알림 종류별 토글 추가(팀 구독과 동일 패턴).
-- 기존 구독자는 3종 전부 받던 동작을 유지하도록 DEFAULT TRUE 로 백필된다.
ALTER TABLE member_match_subscription
    ADD COLUMN set_start_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN set_end_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN live_event_enabled BOOLEAN NOT NULL DEFAULT TRUE;
