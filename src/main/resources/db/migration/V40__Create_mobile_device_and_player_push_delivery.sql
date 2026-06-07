CREATE TABLE member_device (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id          BIGINT       NOT NULL,
    fcm_token          VARCHAR(512) NOT NULL,
    platform           VARCHAR(20)  NOT NULL,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    last_registered_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_device_fcm_token UNIQUE (fcm_token),
    CONSTRAINT fk_member_device_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    INDEX idx_member_device_member_active (member_id, active)
);

CREATE TABLE player_solo_rank_push_delivery (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT       NOT NULL,
    player_id     BIGINT       NOT NULL,
    game_id       VARCHAR(64)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    error_message VARCHAR(500),
    sent_at       DATETIME,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_player_solo_rank_push_delivery
        UNIQUE (member_id, player_id, game_id),
    CONSTRAINT fk_player_solo_rank_push_delivery_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_player_solo_rank_push_delivery_player
        FOREIGN KEY (player_id) REFERENCES players (player_id) ON DELETE CASCADE,
    INDEX idx_player_solo_rank_push_delivery_status (status, created_at)
);
