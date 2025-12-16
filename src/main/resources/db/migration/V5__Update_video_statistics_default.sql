UPDATE video SET view_count = 0 WHERE view_count IS NULL;
UPDATE video SET like_count = 0 WHERE like_count IS NULL;
UPDATE video SET comment_count = 0 WHERE comment_count IS NULL;

-- SQLite에서는 ALTER TABLE로 컬럼 제약조건 수정이 제한적이므로,
-- 여기서는 데이터 업데이트만 수행하고, 엔티티 레벨에서 기본값 처리를 강화합니다.
-- (운영 DB가 MySQL이라면 ALTER TABLE video MODIFY COLUMN ... DEFAULT 0; 등을 사용 가능)
