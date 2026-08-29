-- 댓글 수정 지원. 게시글의 edited_at 과 같은 규칙 — 본문 수정에만 찍고,
-- status 변경(블라인드 등)에는 안 찍어 "(수정됨)" 오탐을 막는다.
-- nullable 컬럼 추가라 INSTANT — 후방호환.
ALTER TABLE community_comment ADD COLUMN edited_at DATETIME(3) NULL;
