CREATE TABLE teams (
    team_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_origin_id VARCHAR(255),
    team_name VARCHAR(100) NOT NULL UNIQUE,
    team_code VARCHAR(10),
    team_image_url VARCHAR(255)
);

CREATE TABLE players (
    player_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_origin_id VARCHAR(255),
    player_name VARCHAR(100) NOT NULL UNIQUE,
    image_url VARCHAR(255)
);

CREATE TABLE champions (
    champion_id BIGINT AUTO_INCREMENT PRIMARY KEY
);

CREATE TABLE games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY
);

CREATE TABLE league_match (
    id VARCHAR(50) PRIMARY KEY,
    league_name VARCHAR(20) NOT NULL,
    match_date DATETIME
);

CREATE INDEX idx_league_match_name_date ON league_match (league_name, match_date DESC);

CREATE TABLE game_team_stat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT
);

CREATE TABLE game_participants (
    participant_game_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT
);

CREATE TABLE league_teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT
);

CREATE TABLE team_external_identity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT,
    source VARCHAR(30),
    external_team_id VARCHAR(100)
);

CREATE TABLE video (
    video_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    published_at DATETIME
);
