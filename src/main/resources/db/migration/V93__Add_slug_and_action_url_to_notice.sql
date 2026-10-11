-- 앱 전용 고정 페이지(광고 안내 등)를 공지 테이블에 얹는다. 백오피스 공지 화면에서 같이 관리한다.
-- slug 가 있는 행은 공지 목록·띠배너에서 빠지고 /api/notices/by-slug/{slug} 로만 조회된다.
-- action_url 은 앱 하단 고정 버튼이 외부 브라우저로 여는 주소(구글폼 등).
-- 후방호환: NULL 허용 컬럼 추가라 롤아웃 중 옛 코드가 INSERT 해도 안전하다. UNIQUE 는 NULL 을 여러 건 허용한다.
ALTER TABLE notice
    ADD COLUMN slug VARCHAR(40) NULL,
    ADD COLUMN action_url VARCHAR(500) NULL,
    ADD CONSTRAINT uk_notice_slug UNIQUE (slug);
