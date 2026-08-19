-- Cloudinary URL 에 박혀 있는 f_auto 를 f_webp 로 못박는다.
--
-- f_auto 는 클라이언트가 보내는 Accept 헤더로 포맷을 고르는데, Flutter 의 dart:io HttpClient 는
-- Accept: image/webp 를 붙이지 않아 원본 PNG 가 그대로 내려온다.
-- 같은 자산 실측 — f_auto 46,610B(PNG) vs f_webp 11,806B(webp).
--
-- 대상은 업로드 시점에 변환을 URL 에 박아 저장한 두 곳(선수 11건, 공지 본문 2건).
-- 회원 프로필(591건)은 저장값이 원본 URL 이고 응답 직렬화 시점에 변환을 끼우므로 여기 대상이 아니다.

UPDATE players
SET image_url = REPLACE(image_url, 'f_auto,', 'f_webp,')
WHERE image_url LIKE '%res.cloudinary.com%'
  AND image_url LIKE '%f_auto,%';

UPDATE notice
SET content = REPLACE(content, 'f_auto,', 'f_webp,')
WHERE content LIKE '%res.cloudinary.com%'
  AND content LIKE '%f_auto,%';
