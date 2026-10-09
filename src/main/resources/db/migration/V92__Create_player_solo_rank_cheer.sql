-- 솔랭 응원(화력). 회원이 (선수, 판)마다 누른 횟수를 한 행에 누적한다.
-- 화면은 판 합계만 보이지만, 나중에 선수·팬 랭킹을 만들 수 있게 회원 단위로 쌓는다.
-- 같은 행은 그 회원만 갱신하므로 핫 로우 경합이 없다. 판 합계는 PK 선두(player_id, game_id) 범위 합산이다.
-- game_id 는 player_solo_rank_game.game_id 와 같은 collation 으로 맞춘다(V89 의 1267 사고).
-- 후방호환: 새 테이블이라 롤아웃 중 옛 코드는 건드리지 않는다.
CREATE TABLE player_solo_rank_cheer (
    player_id    BIGINT      NOT NULL,
    game_id      VARCHAR(64) NOT NULL,
    member_id    BIGINT      NOT NULL,
    cheer_count  BIGINT      NOT NULL DEFAULT 0,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (player_id, game_id, member_id),
    KEY idx_player_solo_rank_cheer_member (member_id, updated_at),
    CONSTRAINT fk_player_solo_rank_cheer_player
        FOREIGN KEY (player_id) REFERENCES players (player_id) ON DELETE CASCADE,
    CONSTRAINT fk_player_solo_rank_cheer_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
