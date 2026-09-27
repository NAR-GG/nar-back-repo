-- V47 이 COLLATE 없이 CHARSET 만 지정해 이 테이블만 utf8mb4_0900_ai_ci(문자셋 기본값)로 만들어졌다.
-- 나머지 테이블은 DB 기본값 utf8mb4_unicode_ci 라, game_id 를 player_riot_account.last_checked_match_id 와
-- 비교하는 솔랭 상태 조회(findLive)가 1267 Illegal mix of collations 로 매번 500 이 났다.
-- 문자열 컬럼은 game_id(영숫자 매치 ID) 하나뿐이라 변환으로 유니크키가 충돌할 값이 없다.
-- 인덱스 컬럼 collation 변경이라 테이블 복사(쓰기 잠금)가 일어난다 — 경기 창을 피해 배포한다.
ALTER TABLE player_solo_rank_game
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
