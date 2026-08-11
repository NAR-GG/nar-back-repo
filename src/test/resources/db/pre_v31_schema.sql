CREATE TABLE teams (
    team_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_origin_id VARCHAR(255),
    team_name VARCHAR(100) NOT NULL UNIQUE,
    team_code VARCHAR(10),
    team_image_url VARCHAR(255)
);

-- role: 프로덕션에는 V12(베이스라인 이전)부터 존재. V66 포지션 백필이 참조한다.
CREATE TABLE players (
    player_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_origin_id VARCHAR(255),
    player_name VARCHAR(100) NOT NULL UNIQUE,
    image_url VARCHAR(255),
    role VARCHAR(20)
);

CREATE TABLE champions (
    champion_id BIGINT AUTO_INCREMENT PRIMARY KEY
);

-- actual_game_start_time: 프로드에는 엔티티 시절부터 존재. V54 소속팀 백필이 참조한다.
CREATE TABLE games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actual_game_start_time DATETIME
);

-- state/blue_score/red_score: 프로드에는 V6(베이스라인 이전)부터 존재.
-- V57 best_of 역산 백필과 V58 set_winners 완봉 백필이 참조한다.
CREATE TABLE league_match (
    id VARCHAR(50) PRIMARY KEY,
    league_name VARCHAR(20) NOT NULL,
    match_date DATETIME,
    state VARCHAR(50),
    blue_score INT DEFAULT 0,
    red_score INT DEFAULT 0
);

CREATE INDEX idx_league_match_name_date ON league_match (league_name, match_date DESC);

CREATE TABLE game_team_stat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT
);

-- player_id/game_id: 프로드에는 엔티티 시절부터 존재. V54 소속팀 백필이 참조한다.
CREATE TABLE game_participants (
    participant_game_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT,
    player_id BIGINT,
    game_id BIGINT
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
