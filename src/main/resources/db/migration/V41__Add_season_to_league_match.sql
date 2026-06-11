-- league_match에 시즌 정보 컬럼 추가 (lolesports 토너먼트 기간으로 채움, 미해석 시 NULL)
ALTER TABLE league_match
    ADD COLUMN season_year INT NULL,
    ADD COLUMN season_split VARCHAR(20) NULL;

-- 시즌 필터 + 커서 페이지네이션용 인덱스
CREATE INDEX idx_league_match_season_date ON league_match (league_name, season_year, season_split, match_date DESC);

-- 커서 정렬(match_date DESC, id DESC)이 filesort 없이 인덱스를 타도록 기존 인덱스에 id 포함
DROP INDEX idx_league_match_name_date ON league_match;
CREATE INDEX idx_league_match_name_date_id ON league_match (league_name, match_date DESC, id DESC);
