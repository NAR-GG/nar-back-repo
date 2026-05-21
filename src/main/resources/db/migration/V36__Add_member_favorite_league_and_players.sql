ALTER TABLE member
    ADD COLUMN favorite_league_name VARCHAR(50);

CREATE TABLE member_favorite_player (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT   NOT NULL,
    player_id   BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT NOW(),
    UNIQUE KEY uq_member_favorite_player (member_id, player_id),
    CONSTRAINT fk_member_favorite_player_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_favorite_player_player
        FOREIGN KEY (player_id) REFERENCES players (player_id)
);
