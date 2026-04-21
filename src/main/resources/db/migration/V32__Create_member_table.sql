CREATE TABLE member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nickname    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(255),
    favorite_team_id BIGINT,
    onboarded_at DATETIME,
    created_at  DATETIME     NOT NULL DEFAULT NOW(),
    FOREIGN KEY (favorite_team_id) REFERENCES teams (team_id)
);
