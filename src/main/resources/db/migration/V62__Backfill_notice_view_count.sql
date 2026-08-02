-- notice id 1 조회수 백필. view_count 컬럼(V61) 도입 전에 발행된 공지라 집계가 0으로 남아 있다.
-- 값 46 = 발행(2026-08-01 09:44) 이후 CloudWatch /nar/app 로그의 `GET /api/notices`(공지 목록 화면 진입) 실측 호출 수.
-- 띠배너 탭 열람은 /api/notices/promoted 응답에 본문이 함께 실려 별도 API 호출이 없어 집계 불가 — 46은 하한이다.
-- 이미 실집계가 쌓였으면 덮어쓰지 않는다.
UPDATE notice SET view_count = 46 WHERE id = 1 AND view_count = 0;
