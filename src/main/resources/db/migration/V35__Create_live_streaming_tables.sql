CREATE TABLE IF NOT EXISTS league_match_game (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id VARCHAR(50) NOT NULL,
    game_id VARCHAR(64) NOT NULL,
    game_order INT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_league_match_game_match
        FOREIGN KEY (match_id) REFERENCES league_match(id) ON DELETE CASCADE,
    CONSTRAINT uk_league_match_game_game_id UNIQUE (game_id),
    CONSTRAINT uk_league_match_game_match_game UNIQUE (match_id, game_id),
    INDEX idx_league_match_game_match_id (match_id),
    INDEX idx_league_match_game_match_order (match_id, game_order)
);

CREATE TABLE IF NOT EXISTS live_game_minute_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id VARCHAR(64) NOT NULL,
    match_id VARCHAR(64),
    league_name VARCHAR(20),
    blue_team_name VARCHAR(120),
    red_team_name VARCHAR(120),
    minute_bucket_utc DATETIME(3) NOT NULL,
    frame_timestamp_utc DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_live_game_minute_bucket UNIQUE (game_id, minute_bucket_utc),
    INDEX idx_live_minute_snapshot_game_frame (game_id, frame_timestamp_utc DESC)
);

CREATE TABLE IF NOT EXISTS live_game_minute_participant_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id BIGINT NOT NULL,
    participant_id INT NOT NULL,
    team_side VARCHAR(8),
    role VARCHAR(20),
    player_name VARCHAR(100),
    esports_player_id VARCHAR(64),
    champion_name VARCHAR(50),
    level INT,
    kills INT,
    deaths INT,
    assists INT,
    total_gold_earned INT,
    creep_score INT,
    kill_participation DOUBLE,
    champion_damage_share DOUBLE,
    item_ids_json TEXT,
    perks_json TEXT,
    CONSTRAINT fk_live_minute_participant_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES live_game_minute_snapshot(id) ON DELETE CASCADE,
    CONSTRAINT uk_live_minute_participant UNIQUE (snapshot_id, participant_id)
);

CREATE TABLE IF NOT EXISTS live_game_object_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id VARCHAR(64) NOT NULL,
    match_id VARCHAR(64),
    league_name VARCHAR(20),
    team_side VARCHAR(8) NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    event_sub_type VARCHAR(50),
    event_order INT NOT NULL,
    value_after INT NOT NULL,
    source_frame_timestamp_utc DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_live_game_object_event UNIQUE (game_id, team_side, event_type, event_order),
    INDEX idx_live_game_object_event_game_time (game_id, source_frame_timestamp_utc, id)
);

CREATE TABLE IF NOT EXISTS live_game_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    live_game_id VARCHAR(64) NOT NULL,
    live_match_id VARCHAR(64),
    live_league_name VARCHAR(20),
    live_blue_team_name VARCHAR(120),
    live_red_team_name VARCHAR(120),
    first_minute_bucket_utc DATETIME(3),
    last_frame_timestamp_utc DATETIME(3),
    internal_game_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    confidence DOUBLE,
    mapping_method VARCHAR(64),
    reason VARCHAR(500),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_live_game_mapping_live_game UNIQUE (live_game_id),
    CONSTRAINT fk_live_game_mapping_internal_game
        FOREIGN KEY (internal_game_id) REFERENCES games(game_id) ON DELETE SET NULL,
    INDEX idx_live_game_mapping_status (status),
    INDEX idx_live_game_mapping_match (live_match_id),
    INDEX idx_live_game_mapping_internal_game (internal_game_id)
);

CREATE TABLE IF NOT EXISTS live_participant_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    live_game_id VARCHAR(64) NOT NULL,
    live_participant_id INT NOT NULL,
    live_team_side VARCHAR(8),
    live_role VARCHAR(20),
    live_player_name VARCHAR(100),
    live_esports_player_id VARCHAR(64),
    live_champion_name VARCHAR(50),
    internal_game_participant_id BIGINT,
    internal_game_id BIGINT,
    internal_player_id BIGINT,
    internal_team_id BIGINT,
    internal_champion_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    confidence DOUBLE,
    mapping_method VARCHAR(64),
    reason VARCHAR(500),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_live_participant_mapping UNIQUE (live_game_id, live_participant_id),
    CONSTRAINT fk_live_participant_mapping_game_participant
        FOREIGN KEY (internal_game_participant_id) REFERENCES game_participants(participant_game_id) ON DELETE SET NULL,
    CONSTRAINT fk_live_participant_mapping_game
        FOREIGN KEY (internal_game_id) REFERENCES games(game_id) ON DELETE SET NULL,
    CONSTRAINT fk_live_participant_mapping_player
        FOREIGN KEY (internal_player_id) REFERENCES players(player_id) ON DELETE SET NULL,
    CONSTRAINT fk_live_participant_mapping_team
        FOREIGN KEY (internal_team_id) REFERENCES teams(team_id) ON DELETE SET NULL,
    CONSTRAINT fk_live_participant_mapping_champion
        FOREIGN KEY (internal_champion_id) REFERENCES champions(champion_id) ON DELETE SET NULL,
    INDEX idx_live_participant_mapping_status (status),
    INDEX idx_live_participant_mapping_game (live_game_id),
    INDEX idx_live_participant_mapping_internal_game (internal_game_id)
);
