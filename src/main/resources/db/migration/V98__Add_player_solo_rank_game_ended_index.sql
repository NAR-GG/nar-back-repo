-- 판이 끝난 계정을 30초마다 찾는다(PlayerSoloTierRefreshService.refreshRecentlyEnded). 최근 30분 범위 조회용.
-- V97 과 나눈 이유: MySQL DDL 은 트랜잭션이 아니라 한 파일에 두면 부분 실패 시 Flyway 재시도가 중복 컬럼으로 막힌다.
CREATE INDEX idx_player_solo_rank_game_ended ON player_solo_rank_game (game_ended_at);
