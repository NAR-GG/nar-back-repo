-- 영상이 유튜브 쇼츠인지. NULL = 아직 판별 전 (스케줄러가 10분마다 채운다 — YoutubeSyncService.classifyPendingShorts).
-- 후방호환: NULL 허용 컬럼 추가라 롤아웃 중 옛 코드가 INSERT 해도 안전하다.
ALTER TABLE video ADD COLUMN is_short TINYINT(1) NULL;

-- 유튜버 쇼츠 채널의 영상은 기존 코드가 전부 /shorts/ 링크로 저장해 왔다. 그 의미를 그대로 옮긴다.
UPDATE video SET is_short = 1
WHERE channel_id IN (SELECT channel_id FROM channel WHERE channel_type = 'SHORTS');
