-- 마이구독 알림 리스트(마이페이지) 피드 테이블.
-- FCM 푸시는 발송 시점에만 콘텐츠(title/body)가 만들어지고 영속화되지 않아,
-- 회원별 알림 히스토리를 조회할 수 없었다. 푸시 발송 성공 시 이 테이블에 1행을 남겨
-- 알림 리스트 전체 페이지 API가 조회한다.
--   - type: SET_START / SET_END / LIVE_EVENT / PLAYER_SOLO_RANK_STARTED (푸시 data.type 과 일치)
--   - data: 딥링크·참조 식별자(playerId/matchId/gameId/setNumber 등) JSON
--   - read_at: 읽음 처리 시각(미읽음이면 NULL)
CREATE TABLE IF NOT EXISTS member_notification (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id  BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       VARCHAR(500),
    data       JSON,
    read_at    DATETIME,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_member_notification_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    INDEX idx_member_notification_member_created (member_id, created_at),
    INDEX idx_member_notification_member_type (member_id, type)
);
