-- 결과를 못 받은 솔랭 판을 DB 에서 되살린다(SoloRankEndNotificationService.recoverUnresolved, 30초마다 최근 2시간).
-- game_ended_at 이 빈 행이 과거분 1.8만이라 V98 인덱스로는 못 좁힌다 — 감지 시각으로 범위를 잡는다.
CREATE INDEX idx_player_solo_rank_game_detected ON player_solo_rank_game (detected_at);
