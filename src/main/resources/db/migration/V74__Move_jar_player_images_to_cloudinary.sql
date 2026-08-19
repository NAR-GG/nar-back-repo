-- JAR 안에만 있던 선수 이미지 9건을 Cloudinary 업로드본으로 옮긴다.
--
-- 이 9명은 lolesports 에 공식 사진이 없어 `/images/players/...` 로 저장돼 있었고, Spring 이 JAR 내부
-- 정적 리소스로 직접 서빙했다. 원본 URL 이 없어 fetch 로는 못 감싸므로 실제로 업로드했다
-- (public_id = players/{playerId}, 앱의 백오피스 업로드와 같은 규칙).
--
-- image_locked 는 건드리지 않는다. 지금 잠긴 3건(Setab·Deft·Rascal)은 그대로 두고, 나머지 6건은
-- 잠기지 않은 채로 둬서 나중에 lolesports 가 공식 사진을 내면 동기화가 자연히 갱신하게 한다.
--
-- 매칭은 player_id 가 아니라 기존 이미지 경로로 한다. 로컬·테스트 DB 의 player_id 가 운영과
-- 다르기 때문이다 — 경로는 어느 DB 에서나 같은 행을 가리킨다.
--
-- V72 와 같은 이유로 콜레이션을 맞추고, 존재 검사 후 실행한다.

SET @@session.collation_connection = @@collation_database;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
	WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'players' AND COLUMN_NAME = 'image_url') = 1,
	'UPDATE players SET image_url = CASE image_url
		WHEN ''/images/players/Mihawk_김주형.webp'' THEN ''https://res.cloudinary.com/dvvurdffw/image/upload/f_webp,q_auto,w_500,c_limit/v1787154500/players/11.webp''
		WHEN ''/images/players/Janus_엄예준.webp'' THEN ''https://res.cloudinary.com/dvvurdffw/image/upload/f_webp,q_auto,w_500,c_limit/v1787154518/players/320.webp''
		WHEN ''/images/players/Setab_송경진.webp'' THEN ''https://res.cloudinary.com/dvvurdffw/image/upload/f_webp,q_auto,w_500,c_limit/v1787154519/players/394.webp''
		WHEN ''/images/players/Vincenzo_하승민.webp'' THEN ''https://res.cloudinary.com/dvvurdffw/image/upload/f_webp,q_auto,w_500,c_limit/v1787154521/players/523.webp''
		WHEN ''/images/players/Quad_송수형.webp'' THEN ''https://res.cloudinary.com/dvvurdffw/image/upload/f_webp,q_auto,w_500,c_limit/v1787154522/players/697.webp''
		WHEN ''/images/players/Quid_임현승.webp'' THEN ''https://res.cloudinary.com/dvvurdffw/image/upload/f_webp,q_auto,w_500,c_limit/v1787154523/players/860.webp''
		WHEN ''/images/players/Jinbeom_천진범.webp'' THEN ''https://res.cloudinary.com/dvvurdffw/image/upload/f_webp,q_auto,w_500,c_limit/v1787154525/players/1879.webp''
		WHEN ''/images/players/Deft_데프트.webp'' THEN ''https://res.cloudinary.com/dvvurdffw/image/upload/f_webp,q_auto,w_500,c_limit/v1787154526/players/3784.webp''
		WHEN ''/images/players/Rascal_라스칼.webp'' THEN ''https://res.cloudinary.com/dvvurdffw/image/upload/f_webp,q_auto,w_500,c_limit/v1787154528/players/3785.webp''
		ELSE image_url END
	 WHERE image_url LIKE ''/images/players/%''',
	'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
