-- league_match에 시즌 정보 컬럼 추가 (lolesports 토너먼트 기간으로 채움, 미해석 시 NULL)
-- 운영 DB는 baseline V30 기준이라 V6의 인덱스가 없을 수 있어 모든 단계를 존재 검사 후 실행한다 (멱등).

-- 1) 시즌 컬럼 (없을 때만 추가)
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'league_match' AND COLUMN_NAME = 'season_year') = 0,
    'ALTER TABLE league_match ADD COLUMN season_year INT NULL, ADD COLUMN season_split VARCHAR(20) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 시즌 필터 + 커서 페이지네이션용 인덱스 (없을 때만 생성)
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'league_match' AND INDEX_NAME = 'idx_league_match_season_date') = 0,
    'CREATE INDEX idx_league_match_season_date ON league_match (league_name, season_year, season_split, match_date DESC)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 기존 (league_name, match_date) 인덱스 제거 (있을 때만)
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'league_match' AND INDEX_NAME = 'idx_league_match_name_date') > 0,
    'DROP INDEX idx_league_match_name_date ON league_match',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 커서 정렬(match_date DESC, id DESC)이 filesort 없이 인덱스를 타도록 id 포함 인덱스 생성 (없을 때만)
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'league_match' AND INDEX_NAME = 'idx_league_match_name_date_id') = 0,
    'CREATE INDEX idx_league_match_name_date_id ON league_match (league_name, match_date DESC, id DESC)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
