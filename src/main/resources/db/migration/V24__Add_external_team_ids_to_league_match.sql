ALTER TABLE league_match
    ADD COLUMN blue_external_team_id VARCHAR(128) NULL,
    ADD COLUMN red_external_team_id VARCHAR(128) NULL;

CREATE INDEX idx_league_match_blue_external_team_id ON league_match (blue_external_team_id);
CREATE INDEX idx_league_match_red_external_team_id ON league_match (red_external_team_id);
