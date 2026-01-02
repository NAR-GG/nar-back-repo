CREATE TABLE league_match (
    id VARCHAR(50) PRIMARY KEY,
    league_name VARCHAR(20) NOT NULL,
    match_title VARCHAR(200),
    match_date DATETIME,
    state VARCHAR(50),
    
    blue_team_code VARCHAR(50),
    blue_team_name VARCHAR(100),
    blue_score INT DEFAULT 0,
    
    red_team_code VARCHAR(50),
    red_team_name VARCHAR(100),
    red_score INT DEFAULT 0,
    
    has_vod BOOLEAN DEFAULT FALSE,
    match_details_json TEXT,
    
    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_league_match_name_date ON league_match(league_name, match_date DESC);
