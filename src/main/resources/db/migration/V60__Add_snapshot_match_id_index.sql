-- 세트 목록(getMatchGames)의 findGameIdsByMatchIdOrderByStart 가
-- WHERE match_id ... GROUP BY game_id ... MIN(frame_timestamp_utc) 로 읽는데
-- match_id 인덱스가 없어 매 호출 풀 인덱스 스캔(프로드 실측 150ms)이었다.
-- 커버링 인덱스로 테이블 접근 없이 해소한다.
ALTER TABLE live_game_minute_snapshot
    ADD INDEX idx_live_minute_snapshot_match_game_frame (match_id, game_id, frame_timestamp_utc);
