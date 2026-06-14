-- KILL 이벤트에 킬러/피해자 선수명과 피해자 챔피언을 함께 저장하기 위한 NULL 허용 컬럼 추가.
-- 기존 행(킬러 챔피언만 event_sub_type에 저장)은 NULL 로 남으며, 모바일 타임라인 API가 NULL 을 허용한다.
ALTER TABLE live_game_object_event
    ADD COLUMN killer_player_name VARCHAR(64) NULL,
    ADD COLUMN victim_champion VARCHAR(40) NULL,
    ADD COLUMN victim_player_name VARCHAR(64) NULL;
