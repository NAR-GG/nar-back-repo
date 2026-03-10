CREATE TABLE IF NOT EXISTS team_external_identity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(50) NOT NULL,
    external_team_id VARCHAR(128) NOT NULL,
    team_id BIGINT NOT NULL,
    external_name_raw VARCHAR(255) NULL,
    matched_by VARCHAR(50) NULL,
    confidence DECIMAL(5,4) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_team_external_identity_source_external_team UNIQUE (source, external_team_id),
    CONSTRAINT fk_team_external_identity_team FOREIGN KEY (team_id) REFERENCES teams (team_id) ON DELETE CASCADE
);

CREATE INDEX idx_team_external_identity_team_id ON team_external_identity (team_id);
CREATE INDEX idx_team_external_identity_source_team_id ON team_external_identity (source, team_id);
