-- 라이브 디스커버리 스케줄러가 10초마다 matchDate 범위로 경기 있는 리그를
-- distinct 조회한다(findDistinctLeagueNamesByDateRange). 기존 인덱스는 모두
-- league_name 선두 복합이라 이 쿼리는 full table scan을 탄다.
-- (match_date, league_name) 인덱스로 range + covering(DISTINCT league_name) 처리한다.
SET @stmt := IF(
    (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'league_match'
       AND INDEX_NAME = 'idx_league_match_date_league') = 0,
    'CREATE INDEX idx_league_match_date_league ON league_match (match_date, league_name)',
    'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
