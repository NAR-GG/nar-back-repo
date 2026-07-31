-- 매치의 다전제 규격(Bo1/Bo3/Bo5). lolesports getSchedule/getEventDetails 의 match.strategy.count 값이다.
-- 마지막 세트·매치포인트 판정에 필요하다. 같은 리그 안에 Bo 가 섞이므로(KeSPA Cup: 그룹 Bo1 25 / 플레이-인 Bo3 10 / 토너먼트·결승 Bo5 3,
-- LCK: 정규 Bo3 / 플레이오프 Bo5) 리그명으로는 판정할 수 없다.
ALTER TABLE league_match ADD COLUMN best_of INT NULL;

-- 완료 경기 역산 백필. 승자는 정확히 ceil(bo/2) 세트를 따고 끝나므로 bo = 2 * max(score) - 1 이다.
-- 프로덕션 completed 3,274건 전수 점검: max 값이 1/2/3 만 존재(동점·max>3·NULL 0건),
-- API strategy.count 와 대조한 65건 전수 일치. 미완료 경기는 다음 sync 가 업스트림 값으로 채운다.
UPDATE league_match
   SET best_of = 2 * GREATEST(blue_score, red_score) - 1
 WHERE state = 'completed'
   AND blue_score IS NOT NULL
   AND red_score IS NOT NULL
   AND GREATEST(blue_score, red_score) > 0;
