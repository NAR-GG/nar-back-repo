ALTER TABLE player_riot_account
    MODIFY summoner_id VARCHAR(128) NULL;

ALTER TABLE player_riot_account
    ADD COLUMN last_checked_match_id VARCHAR(64) NULL,
    ADD COLUMN last_alerted_match_id VARCHAR(64) NULL,
    ADD COLUMN last_match_checked_at DATETIME NULL;

CREATE INDEX idx_player_riot_account_last_checked_match_id
    ON player_riot_account (last_checked_match_id);
