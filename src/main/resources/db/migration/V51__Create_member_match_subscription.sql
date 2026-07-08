-- 특정 경기 예약 알림. 팀 구독과 별개로, 유저가 개별 경기를 구독하면
-- 그 경기의 세트 시작/종료/라이브 이벤트를 받는다(토글 없이 3종 전부).
CREATE TABLE member_match_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    match_id VARCHAR(50) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_member_match_subscription
        UNIQUE (member_id, match_id),
    CONSTRAINT fk_member_match_subscription_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_match_subscription_match
        FOREIGN KEY (match_id) REFERENCES league_match(id) ON DELETE CASCADE,
    INDEX idx_member_match_subscription_member (member_id),
    INDEX idx_member_match_subscription_match (match_id)
);
