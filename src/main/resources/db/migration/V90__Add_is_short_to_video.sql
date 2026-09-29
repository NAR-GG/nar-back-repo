-- 영상이 유튜브 쇼츠인지. NULL = 아직 판별 전 (스케줄러가 10분마다 채운다 — YoutubeSyncService.classifyPendingShorts).
-- 후방호환: NULL 허용 컬럼 추가라 롤아웃 중 옛 코드가 INSERT 해도 안전하다.
-- 유튜버 쇼츠 채널(channel_type = 'SHORTS') 영상은 조회·판별 대상에서 코드가 제외하므로 값을 채우지 않는다.
ALTER TABLE video ADD COLUMN is_short TINYINT(1) NULL;
