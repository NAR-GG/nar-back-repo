-- 이미 저장된 Riot 계열 이미지 URL 을 Cloudinary fetch 로 감싼다.
--
-- 코드는 쓰기 시점부터 감싸지만, 그것만으로는 지난 경기 행(league_match 3,545건)이 영원히 원본으로 남는다.
-- 팀·챔피언·선수는 다음 동기화 때 자연히 갱신되지만 지난 경기는 다시 쓰이지 않기 때문이다.
--
-- 실측 절감 — 팀 130,938B → 7,998B / 선수 276,907B → 14,660B
--            챔피언 25,644B → 2,042B / 스플래시 91,088B(1280×720 가로) → 17,492B(400×600 세로)
--
-- cloud name 은 환경마다 다르다. Flyway placeholder 로 주입하면 Spring 없이 Flyway 를 직접 구성하는
-- 스키마 통합 테스트(MemberTeamNotificationSchemaMySqlIntegrationTest 등)에서 미해결로 터지므로,
-- 이미 저장돼 있는 Cloudinary 업로드 URL 에서 뽑아 쓴다. 회원 프로필이 없으면 선수 이미지에서 찾고,
-- 둘 다 없으면(테스트·로컬처럼 Cloudinary 를 안 쓰는 DB) NULL 이 되어 모든 문장이 0행에 걸린다.
--
-- V41 과 같은 이유로 모든 단계를 존재 검사 후 실행한다. baseline V30 스냅샷에는 league_match 의
-- 팀 이미지 컬럼과 champions 테이블이 없어, 검사 없이 UPDATE 하면 마이그레이션 체인이 통째로 깨진다.
--
-- 되돌리기 — CLOUDINARY_CDN_ENABLED=false 로 내린 뒤 아래를 실행한다.
--   UPDATE league_match SET blue_team_image_url =
--     CONCAT('https://', SUBSTRING_INDEX(blue_team_image_url, '/https://', -1))
--   WHERE blue_team_image_url LIKE '%/image/fetch/%';
--   (red_team_image_url, teams.team_image_url, players.image_url,
--    champions.image_url, champions.loading_image_url 도 같은 모양)

SET @cloud = COALESCE(
	(SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(profile_image_url, 'res.cloudinary.com/', -1), '/', 1)
	 FROM member WHERE profile_image_url LIKE 'https://res.cloudinary.com/%' LIMIT 1),
	(SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(image_url, 'res.cloudinary.com/', -1), '/', 1)
	 FROM players WHERE image_url LIKE 'https://res.cloudinary.com/%' LIMIT 1));

SET @prefix = CONCAT('https://res.cloudinary.com/', @cloud, '/image/fetch/');
SET @configured = (@cloud IS NOT NULL AND @cloud <> '');

-- 변환·패턴을 세션 변수로 빼서 아래 동적 SQL 문자열에 따옴표가 섞이지 않게 한다.
SET @t_team = 'f_webp,q_auto,w_200,c_limit/';
SET @t_player = 'f_webp,q_auto,w_500,c_limit/';
SET @t_champion = 'f_webp,q_auto,w_128,c_limit/';
SET @t_splash = 'f_webp,q_auto,w_400,h_600,c_fill,g_auto/';
SET @from_lolesports = 'https://static.lolesports.com/%';
SET @from_ddragon = 'https://ddragon.leagueoflegends.com/%';
SET @from_cdragon = 'https://cdn.communitydragon.org/%';

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
	WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teams' AND COLUMN_NAME = 'team_image_url') = 1,
	'UPDATE teams SET team_image_url = CONCAT(@prefix, @t_team, team_image_url)
	 WHERE @configured AND team_image_url LIKE @from_lolesports',
	'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
	WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'league_match' AND COLUMN_NAME = 'blue_team_image_url') = 1,
	'UPDATE league_match SET blue_team_image_url = CONCAT(@prefix, @t_team, blue_team_image_url)
	 WHERE @configured AND blue_team_image_url LIKE @from_lolesports',
	'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
	WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'league_match' AND COLUMN_NAME = 'red_team_image_url') = 1,
	'UPDATE league_match SET red_team_image_url = CONCAT(@prefix, @t_team, red_team_image_url)
	 WHERE @configured AND red_team_image_url LIKE @from_lolesports',
	'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 선수는 lolesports 원본만 대상이다. Cloudinary 업로드본(11건)과 JAR 내부 경로(9건)는 건드리지 않는다.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
	WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'players' AND COLUMN_NAME = 'image_url') = 1,
	'UPDATE players SET image_url = CONCAT(@prefix, @t_player, image_url)
	 WHERE @configured AND image_url LIKE @from_lolesports',
	'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
	WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'champions' AND COLUMN_NAME = 'image_url') = 1,
	'UPDATE champions SET image_url = CONCAT(@prefix, @t_champion, image_url)
	 WHERE @configured AND image_url LIKE @from_ddragon',
	'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
	WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'champions' AND COLUMN_NAME = 'loading_image_url') = 1,
	'UPDATE champions SET loading_image_url = CONCAT(@prefix, @t_splash, loading_image_url)
	 WHERE @configured AND loading_image_url LIKE @from_cdragon',
	'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
