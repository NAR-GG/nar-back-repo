-- 알림 리스트 조회(MemberNotificationService.getNotifications)는 요청마다 미읽음 건수를
-- countByMember_IdAndReadAtIsNull 로 센다. 기존 인덱스는 (member_id, created_at)/(member_id, type)
-- 뿐이라 read_at 을 확인하려고 member 의 전체 행마다 클러스터드 인덱스를 랜덤 액세스했다.
--
-- 실측 2026-08-12 23:53 (LPL 라이브 유입, 27 req/s): 이 엔드포인트가 241건 중 평균 294ms,
-- 최대 7,297ms 이고 그 시간의 88%가 SQL 대기였다. 프로덕션 DB 에서 미읽음 5,793건 회원으로
-- 직접 재보니 미읽음 카운트만 2,130ms — 전체 카운트(4.5ms)·페이지 20건(2.4ms)과 비교해 500배.
-- member_notification 이 125만 행 632MB 인데 innodb_buffer_pool_size 가 128MB 라
-- 랜덤 액세스가 그대로 디스크 I/O 로 나간다.
--
-- (member_id, read_at) 을 추가하면 커버링 인덱스가 되어 2,130ms → 5.2ms (EXPLAIN: Using index).
--
-- 프로덕션에는 2026-08-13 00:21 에 온라인 DDL 로 선반영했다(34.6초, ALGORITHM=INPLACE LOCK=NONE).
-- 배포 시 중복 생성으로 실패하지 않도록 V50 과 같은 멱등 패턴으로 둔다.
SET @stmt := IF(
    (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_notification'
       AND INDEX_NAME = 'idx_member_notification_member_read') = 0,
    'CREATE INDEX idx_member_notification_member_read ON member_notification (member_id, read_at)',
    'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
