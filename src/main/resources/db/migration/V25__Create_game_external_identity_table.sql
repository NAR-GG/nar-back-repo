CREATE TABLE IF NOT EXISTS game_external_identity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(50) NOT NULL,
    external_game_id VARCHAR(128) NOT NULL,
    game_id BIGINT NOT NULL,
    external_match_id VARCHAR(128) NULL,
    external_league_name VARCHAR(50) NULL,
    match_date DATE NULL,
    game_order INT NULL,
    matched_by VARCHAR(50) NULL,
    confidence DECIMAL(5,4) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_game_external_identity_source_external_game UNIQUE (source, external_game_id),
    CONSTRAINT fk_game_external_identity_game FOREIGN KEY (game_id) REFERENCES games (game_id) ON DELETE CASCADE
);

CREATE INDEX idx_game_external_identity_game_id ON game_external_identity (game_id);
CREATE INDEX idx_game_external_identity_source_game_id ON game_external_identity (source, game_id);
CREATE INDEX idx_game_external_identity_source_match_id ON game_external_identity (source, external_match_id);
