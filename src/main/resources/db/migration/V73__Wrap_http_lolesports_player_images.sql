-- V72 가 놓친 http 원본을 감싼다.
--
-- lolesports 는 같은 자산을 http 로도 내려주고, 선수 이미지 71건이 그렇게 저장돼 있었다.
-- V72 의 조건이 'https://static.lolesports.com/%' 였던 탓에 이 71건만 원본으로 남았다
-- (팀·경기·챔피언에는 http 가 없어 영향이 없었다).
--
-- 스킴은 https 로 맞춘다. 스킴만 다른 같은 자산이 두 벌로 갈리면 저장값도 Cloudinary 캐시 키도
-- 갈리기 때문이고, 같은 경로가 https 로도 200 인 것을 확인했다. 앱 코드도 같은 규칙으로 감싼다
-- (ImageCdn.toHttps).
--
-- V72 와 같은 이유로 콜레이션을 맞추고, 존재 검사 후 실행한다.

SET @@session.collation_connection = @@collation_database;

SET @cloud = COALESCE(
	(SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(profile_image_url, 'res.cloudinary.com/', -1), '/', 1)
	 FROM member WHERE profile_image_url LIKE 'https://res.cloudinary.com/%' LIMIT 1),
	(SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(image_url, 'res.cloudinary.com/', -1), '/', 1)
	 FROM players WHERE image_url LIKE 'https://res.cloudinary.com/%' LIMIT 1));

SET @prefix = CONCAT('https://res.cloudinary.com/', @cloud, '/image/fetch/');
SET @configured = (@cloud IS NOT NULL AND @cloud <> '');
SET @t_player = 'f_webp,q_auto,w_500,c_limit/';
SET @from_http_lolesports = 'http://static.lolesports.com/%';

-- SUBSTRING(image_url, 8) 이 'http://' 7글자를 떼어낸다.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
	WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'players' AND COLUMN_NAME = 'image_url') = 1,
	'UPDATE players SET image_url = CONCAT(@prefix, @t_player, CONCAT(''https://'', SUBSTRING(image_url, 8)))
	 WHERE @configured AND image_url LIKE @from_http_lolesports',
	'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
