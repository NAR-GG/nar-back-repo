-- 백오피스 선수 검색의 리그 필터(출전 기록 EXISTS)용 커버링 인덱스.
-- 기존 경로는 idx_game_champion(game_id)로 찾은 뒤 player_id를 행 룩업으로 읽어
-- LCK 기준 참가기록 ~9천 행의 랜덤 액세스가 발생했다(페이지당 목록+카운트 2회).
-- (game_id, player_id) 커버링으로 인덱스만 읽게 되어 실측 98ms → 15ms (동규모 로컬).
CREATE INDEX idx_gp_game_player ON game_participants (game_id, player_id);
