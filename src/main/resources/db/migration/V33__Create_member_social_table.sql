CREATE TABLE member_social (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT       NOT NULL,
    provider    VARCHAR(20)  NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    UNIQUE KEY uq_provider (provider, provider_id),
    FOREIGN KEY (member_id) REFERENCES member (id)
);
