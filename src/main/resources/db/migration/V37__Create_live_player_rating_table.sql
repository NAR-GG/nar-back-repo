CREATE TABLE live_player_rating (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    live_game_id VARCHAR(64) NOT NULL,
    live_participant_id INT NOT NULL,
    member_id BIGINT NOT NULL,
    internal_player_id BIGINT,
    team_side VARCHAR(8),
    role VARCHAR(20),
    player_name VARCHAR(100) NOT NULL,
    esports_player_id VARCHAR(64),
    champion_name VARCHAR(50),
    rating TINYINT NOT NULL,
    comment VARCHAR(150),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_live_player_rating_member_target
        UNIQUE (live_game_id, live_participant_id, member_id),
    CONSTRAINT fk_live_player_rating_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT fk_live_player_rating_player
        FOREIGN KEY (internal_player_id) REFERENCES players(player_id) ON DELETE SET NULL,
    CONSTRAINT chk_live_player_rating_value CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_live_player_rating_target (live_game_id, live_participant_id),
    INDEX idx_live_player_rating_game (live_game_id),
    INDEX idx_live_player_rating_member (member_id)
);
