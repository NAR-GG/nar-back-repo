-- 라이브 경기 팀 이벤트 FCM 푸시(#21) 멱등 처리 테이블.
-- player_solo_rank_push_delivery 패턴을 복제했다.
-- 멱등 키는 (member_id, match_id, set_number, event_type, event_order):
--   - event_type: SET_START / SET_END / LIVE_EVENT
--   - event_order: LIVE_EVENT 는 충돌 없는 전역 순번으로 live_game_object_event.id 를 사용한다(킬/오브젝트 혼선 방지).
--     SET_START/SET_END 는 세트 내 순번이 없으므로 0 상수를 사용한다.
--     (MySQL UNIQUE 는 NULL 을 서로 다른 값으로 취급해 멱등이 깨지므로 NULL 대신 0 을 쓴다.)
-- 키가 팀이 아니라 match_id 라서, 한 회원이 한 경기의 양 팀을 모두 구독해도 이벤트당 1번만 발송된다.
CREATE TABLE member_team_event_push_delivery (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT       NOT NULL,
    match_id      VARCHAR(64)  NOT NULL,
    set_number    INT          NOT NULL,
    event_type    VARCHAR(20)  NOT NULL,
    event_order   BIGINT       NOT NULL DEFAULT 0,
    status        VARCHAR(20)  NOT NULL,
    error_message VARCHAR(500),
    sent_at       DATETIME,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_team_event_push_delivery
        UNIQUE (member_id, match_id, set_number, event_type, event_order),
    CONSTRAINT fk_member_team_event_push_delivery_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    INDEX idx_member_team_event_push_delivery_status (status, created_at)
);
