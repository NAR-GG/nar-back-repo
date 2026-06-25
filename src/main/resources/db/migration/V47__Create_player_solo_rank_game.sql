-- 추적 선수의 솔로 랭크 게임 이력. 라이브 모니터가 새 솔랭 감지 시 1행 적재(구독 무관).
-- 선수 카드 "최근 솔랭 / 챔프 폭"용. 승패·KDA 등 결과는 담지 않음(spectator API 한계).
CREATE TABLE IF NOT EXISTS player_solo_rank_game (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    player_id   BIGINT       NOT NULL,
    game_id     VARCHAR(64)  NOT NULL,
    champion_id BIGINT       NULL,
    detected_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_player_solo_rank_game UNIQUE (player_id, game_id),
    INDEX idx_player_solo_rank_game_player_detected (player_id, detected_at),
    CONSTRAINT fk_player_solo_rank_game_player
        FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE,
    CONSTRAINT fk_player_solo_rank_game_champion
        FOREIGN KEY (champion_id) REFERENCES champion (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
