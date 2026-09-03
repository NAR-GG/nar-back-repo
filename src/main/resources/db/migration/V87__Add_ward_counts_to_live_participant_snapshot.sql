-- 피드 details 프레임의 wardsPlaced / wardsDestroyed 를 분 스냅샷에 같이 적는다.
-- 지금까지 파싱하지 않고 버리던 값. 시야점수는 아니다(개수). 추가 컬럼만이라 롤아웃 중 옛 코드와 공존한다.
-- 과거 행은 NULL — 종료 경기는 CSV game_player_stat.wards_placed / wards_killed 로 채울 수 있다.
ALTER TABLE live_game_minute_participant_snapshot
    ADD COLUMN wards_placed INT NULL AFTER champion_damage_share,
    ADD COLUMN wards_destroyed INT NULL AFTER wards_placed;
