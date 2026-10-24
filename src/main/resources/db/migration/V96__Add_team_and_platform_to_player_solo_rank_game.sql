-- 선수 페이지 "솔랭에서 만난 선수" — 같은 판에 함께한 추적 선수가 같은 편이었는지 상대였는지 가르려고
-- 판 결과(match-v5)의 팀(100/200)과, 판 id 가 플랫폼마다 따로 매겨지므로 그 판의 플랫폼을 함께 남긴다.
-- 컬럼 추가뿐이라 롤아웃 중 옛 코드와 공존한다. 기존 행은 null 로 두고 관리자 백필로 채운다.
ALTER TABLE player_solo_rank_game
    ADD COLUMN team_id INT NULL,
    ADD COLUMN platform VARCHAR(8) NULL;

-- 만난 선수 조회가 같은 판의 다른 선수 행을 game_id 로 찾는다. 기존 유니크(player_id, game_id)는 선두가 player_id 라 못 탄다.
CREATE INDEX idx_player_solo_rank_game_game ON player_solo_rank_game (game_id);
