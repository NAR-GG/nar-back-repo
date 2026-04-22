CREATE TABLE refresh_token (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT       NOT NULL,
    token       VARCHAR(512) NOT NULL UNIQUE,
    expires_at  DATETIME     NOT NULL,
    FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
);
