-- 유튜브 공식 채널이 어느 팀 것인지. 앱이 채널명을 팀 코드·팀명과 문자열로 대조하던 것을 서버가 대신 준다.
-- (Hanwha Life Esports ↔ HLE, DRX ↔ KRX, 브리온 ↔ BRO, DN SOOPers ↔ DNS 는 문자열 대조가 실패한다.)
-- 코드는 teams.team_code 와 같은 체계다. LCK 공식 채널은 팀이 아니라 NULL 로 둔다.
-- 후방호환: NULL 허용 컬럼 추가라 롤아웃 중 옛 코드가 INSERT 해도 안전하다.
-- 채널이 새로 시드되면(로스터 변경 등) 이 매핑도 함께 넣어야 한다.
ALTER TABLE channel ADD COLUMN team_code VARCHAR(10) NULL;

UPDATE channel SET team_code = 'T1'  WHERE youtube_channel_id = 'UCJprx3bX49vNl6Bcw01Cwfg';
UPDATE channel SET team_code = 'HLE' WHERE youtube_channel_id = 'UCrfB1-zWijAYkgfZW7Ehc8Q';
UPDATE channel SET team_code = 'GEN' WHERE youtube_channel_id = 'UCDmmbxGg8g-EBkC_ku6vybg';
UPDATE channel SET team_code = 'KT'  WHERE youtube_channel_id = 'UC8FErYSi74YwGUAoTpjvgzQ';
UPDATE channel SET team_code = 'NS'  WHERE youtube_channel_id = 'UC4PoHC-R9EeJYTuUv3ndmJw';
UPDATE channel SET team_code = 'DK'  WHERE youtube_channel_id = 'UCepHesz_5Lwr7qRaqjB-p1A';
UPDATE channel SET team_code = 'KRX' WHERE youtube_channel_id = 'UC5WN-znPsJK0BbA8aHxZHWQ';
UPDATE channel SET team_code = 'BFX' WHERE youtube_channel_id = 'UCxedTJNaGRHiq6YfNtQVCNA';
UPDATE channel SET team_code = 'BRO' WHERE youtube_channel_id = 'UCYQO6n0KZmwfwzWtm4_nAPA';
UPDATE channel SET team_code = 'DNS' WHERE youtube_channel_id = 'UCGW76VChAJKee9kYzvyoycQ';
