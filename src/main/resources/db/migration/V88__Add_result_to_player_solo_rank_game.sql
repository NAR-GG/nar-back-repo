-- 솔랭 게임의 실제 시작 시각과 결과. 홈 "구독 선수 솔랭" 카드가 쓴다.
--
-- 모니터는 spectator(gameStartTime)와 match-v5(승패·KDA·시작/종료)를 이미 받고 있었는데
-- 푸시 문구에만 쓰고 버렸다. 이제 저장한다. 전부 NULL 허용이라 기존 행은 그대로이고,
-- MySQL 8 에서 INSTANT 로 끝난다(테이블 재작성 없음).
--
-- detected_at 은 그대로 둔다 — 모니터가 "감지한" 시각이라 실제 시작과 다르다(로딩 화면이면
-- 더 이르고, 폴링 주기만큼 늦을 수도 있다). 앱은 game_started_at 이 없을 때만 폴백으로 쓴다.
ALTER TABLE player_solo_rank_game
    ADD COLUMN game_started_at  DATETIME NULL,
    ADD COLUMN game_ended_at    DATETIME NULL,
    ADD COLUMN duration_seconds INT      NULL,
    ADD COLUMN win              BOOLEAN  NULL,
    ADD COLUMN kills            INT      NULL,
    ADD COLUMN deaths           INT      NULL,
    ADD COLUMN assists          INT      NULL;
