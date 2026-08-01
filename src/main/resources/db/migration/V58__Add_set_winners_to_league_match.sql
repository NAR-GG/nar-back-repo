-- 세트별 승자 기록. 콤마 구분 문자열, index = 세트 번호 (예: "B,R,B" = 1세트 블루·2세트 레드·3세트 블루).
-- B/R 은 league_match 의 blue/red 기준, '?' 는 순서 미상(동기화 공백 중 두 세트 이상 지나간 경우).
-- 업스트림(getEventDetails/getGames/getCompletedEvents)은 세트별 승자를 주지 않아(전수 실측)
-- 스코어 전이 시점(60초 sync + 네이버 오버레이)에 어느 팀이 +1 됐는지를 직접 적는다.
ALTER TABLE league_match ADD COLUMN set_winners VARCHAR(32) NULL;

-- 완봉 완료 경기는 모든 세트 승자가 이긴 팀 — 과거 데이터 즉시 백필.
UPDATE league_match
   SET set_winners = CASE
       WHEN red_score = 0 THEN TRIM(TRAILING ',' FROM REPEAT('B,', blue_score))
       ELSE TRIM(TRAILING ',' FROM REPEAT('R,', red_score))
   END
 WHERE state = 'completed'
   AND blue_score IS NOT NULL AND red_score IS NOT NULL
   AND LEAST(blue_score, red_score) = 0
   AND GREATEST(blue_score, red_score) > 0;
