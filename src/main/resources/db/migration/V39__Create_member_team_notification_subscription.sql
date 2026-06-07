CREATE TABLE member_team_notification_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    set_start_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    set_end_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    live_event_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_member_team_notification_subscription
        UNIQUE (member_id, team_id),
    CONSTRAINT fk_member_team_notification_subscription_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_team_notification_subscription_team
        FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
    INDEX idx_member_team_notification_subscription_member (member_id),
    INDEX idx_member_team_notification_subscription_team (team_id)
);

INSERT INTO member_team_notification_subscription (
    member_id,
    team_id,
    set_start_enabled,
    set_end_enabled,
    live_event_enabled
)
SELECT
    m.id,
    m.favorite_team_id,
    TRUE,
    TRUE,
    FALSE
FROM member m
JOIN teams t ON t.team_id = m.favorite_team_id
WHERE UPPER(t.team_code) IN ('T1', 'HLE', 'GEN', 'DK', 'KT', 'DNS', 'BFX', 'NS', 'BRO', 'KRX');
