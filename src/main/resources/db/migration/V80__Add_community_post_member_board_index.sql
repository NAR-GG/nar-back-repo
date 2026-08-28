-- 글 작성 간격(D-9)을 게시판별로 재면서 필요해진 인덱스.
--
-- 조회는 "이 회원이 이 게시판에 마지막으로 쓴 시각" 한 행이다:
--   SELECT created_at FROM community_post
--    WHERE member_id = ? AND board_team_id <=> ?
--    ORDER BY id DESC LIMIT 1
--
-- 기존 idx_community_post_member (member_id, id DESC) 로도 답은 나오지만, 그 회원의
-- 글을 최신부터 훑으며 게시판이 맞는 행을 찾는 스캔이 된다. 전체 게시판에만 쓰다가
-- 팀 게시판에 처음 쓰는 경우처럼 맞는 행이 없으면 그 회원의 글을 끝까지 다 읽는다.
--
-- (member_id, board_team_id, id DESC) 면 세 컬럼이 다 인덱스 안에 있어 첫 행에서 끝난다.
-- board_team_id 가 NULL(전체 게시판)이어도 InnoDB 인덱스는 NULL 을 저장하므로 <=> 가 탄다.
--
-- 기존 인덱스는 남긴다 — "내가 쓴 글"(마이페이지)이 게시판 구분 없이 그것을 쓴다.
ALTER TABLE community_post
    ADD INDEX idx_community_post_member_board (member_id, board_team_id, id DESC);
