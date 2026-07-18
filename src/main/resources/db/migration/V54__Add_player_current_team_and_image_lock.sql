-- 선수 "소속팀" 개념 신설 + 수동 이미지 잠금.
-- current_team_id: 백오피스에서 수동 관리하는 현재 소속팀. 경기 기록(game_participants)과 무관하며 sync가 건드리지 않는다.
-- image_locked: true면 자동 동기화(epromatch/디스크 마이그레이션 등)가 image_url을 덮어쓰지 못한다.
ALTER TABLE players
    ADD COLUMN current_team_id BIGINT NULL,
    ADD COLUMN image_locked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_players_current_team FOREIGN KEY (current_team_id) REFERENCES teams (team_id);

-- 백필: 선수별 가장 최근 경기의 팀(전 리그). 경기 기록 없는 선수는 NULL 유지.
UPDATE players p
JOIN (
    SELECT playerId, teamId FROM (
        SELECT gp.player_id AS playerId,
               gp.team_id   AS teamId,
               ROW_NUMBER() OVER (
                   PARTITION BY gp.player_id
                   ORDER BY g.actual_game_start_time DESC, g.game_id DESC
               ) AS rn
        FROM game_participants gp
        JOIN games g ON gp.game_id = g.game_id
    ) ranked
    WHERE rn = 1
) latest ON latest.playerId = p.player_id
SET p.current_team_id = latest.teamId;
