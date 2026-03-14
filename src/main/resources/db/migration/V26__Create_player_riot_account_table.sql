CREATE TABLE player_riot_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    riot_id VARCHAR(120) NOT NULL,
    game_name VARCHAR(100) NOT NULL,
    tag_line VARCHAR(32) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    puuid VARCHAR(128) NOT NULL,
    summoner_id VARCHAR(128) NOT NULL,
    primary_account BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    live_status VARCHAR(30) NOT NULL DEFAULT 'OFFLINE',
    current_game_id BIGINT NULL,
    last_alerted_game_id BIGINT NULL,
    last_live_checked_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_player_riot_account_player FOREIGN KEY (player_id) REFERENCES players(player_id),
    CONSTRAINT uk_player_riot_account_player UNIQUE (player_id),
    CONSTRAINT uk_player_riot_account_puuid UNIQUE (puuid)
);

CREATE INDEX idx_player_riot_account_platform_enabled
    ON player_riot_account (platform, enabled, primary_account);

CREATE INDEX idx_player_riot_account_summoner_id
    ON player_riot_account (summoner_id);
