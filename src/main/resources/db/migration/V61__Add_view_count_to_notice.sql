-- 공지 조회수. 앱이 공지 상세를 열 때 POST /api/notices/{id}/view 로 1 증가시킨다.
-- 비회원도 보는 공개 공지라 회원 단위 중복 제거는 하지 않는다(순수 열람 횟수).
ALTER TABLE notice ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
