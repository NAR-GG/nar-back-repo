-- 1. GameTeamStat (game_team_stat)
ALTER TABLE game_team_stat ADD COLUMN damage_to_towers INTEGER;
-- ALTER TABLE game_team_stat DROP COLUMN is_first_top_tower; -- 삭제 취소 (하위 호환성)
-- ALTER TABLE game_team_stat DROP COLUMN is_first_bot_tower; -- 삭제 취소 (하위 호환성)

-- 2. GamePlayerStat (game_player_stat)
ALTER TABLE game_player_stat ADD COLUMN damage_to_towers INTEGER;

-- 3. SyncStatus 초기화 (재동기화 유도 - 2026년 데이터 수집 위해)
DELETE FROM sync_status;