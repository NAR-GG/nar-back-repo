-- 이미 저장된 Riot 계열 이미지 URL 을 Cloudinary fetch 로 감싼다.
--
-- 코드는 쓰기 시점부터 감싸지만, 그것만으로는 지난 경기 행(league_match 3,545건)이 영원히 원본으로 남는다.
-- 팀·챔피언·선수는 다음 동기화 때 자연히 갱신되지만 지난 경기는 다시 쓰이지 않기 때문이다.
--
-- 실측 절감 — 팀 130,938B → 7,998B / 선수 276,907B → 14,660B
--            챔피언 25,644B → 2,042B / 스플래시 91,088B(1280×720 가로) → 17,492B(400×600 세로)
--
-- cloud name 은 환경마다 다르므로 Flyway placeholder 로 주입한다(spring.flyway.placeholders).
-- 비어 있으면(로컬 dev 등 Cloudinary 미설정) 모든 문장이 0행에 걸려 아무것도 하지 않는다.
--
-- 되돌리기 — CLOUDINARY_CDN_ENABLED=false 로 내린 뒤 아래를 실행한다.
--   UPDATE league_match SET blue_team_image_url =
--     CONCAT('https://', SUBSTRING_INDEX(blue_team_image_url, '/https://', -1))
--   WHERE blue_team_image_url LIKE '%/image/fetch/%';
--   (red_team_image_url, teams.team_image_url, players.image_url,
--    champions.image_url, champions.loading_image_url 도 같은 모양)

SET @prefix = CONCAT('https://res.cloudinary.com/', '${cloudinaryCloudName}', '/image/fetch/');
SET @configured = ('${cloudinaryCloudName}' <> '');

UPDATE teams
SET team_image_url = CONCAT(@prefix, 'f_webp,q_auto,w_200,c_limit/', team_image_url)
WHERE @configured
  AND team_image_url LIKE 'https://static.lolesports.com/%';

UPDATE league_match
SET blue_team_image_url = CONCAT(@prefix, 'f_webp,q_auto,w_200,c_limit/', blue_team_image_url)
WHERE @configured
  AND blue_team_image_url LIKE 'https://static.lolesports.com/%';

UPDATE league_match
SET red_team_image_url = CONCAT(@prefix, 'f_webp,q_auto,w_200,c_limit/', red_team_image_url)
WHERE @configured
  AND red_team_image_url LIKE 'https://static.lolesports.com/%';

-- 선수는 lolesports 원본만 대상이다. Cloudinary 업로드본(11건)과 JAR 내부 경로(9건)는 건드리지 않는다.
UPDATE players
SET image_url = CONCAT(@prefix, 'f_webp,q_auto,w_500,c_limit/', image_url)
WHERE @configured
  AND image_url LIKE 'https://static.lolesports.com/%';

UPDATE champions
SET image_url = CONCAT(@prefix, 'f_webp,q_auto,w_128,c_limit/', image_url)
WHERE @configured
  AND image_url LIKE 'https://ddragon.leagueoflegends.com/%';

UPDATE champions
SET loading_image_url = CONCAT(@prefix, 'f_webp,q_auto,w_400,h_600,c_fill,g_auto/', loading_image_url)
WHERE @configured
  AND loading_image_url LIKE 'https://cdn.communitydragon.org/%';
