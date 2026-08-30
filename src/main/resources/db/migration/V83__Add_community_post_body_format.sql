-- 작성 툴 고도화(블록 본문): body 에 블록 JSON 이 들어갈 수 있게 되면서
-- 목록 미리보기를 body 절단으로 만들 수 없다 — 저장 시 계산한 preview 를 둔다.
-- body 는 블록 JSON(텍스트 1만자 + 이미지/링크/임베드 메타)이 TEXT(64KB)를
-- 넘을 수 있어 MEDIUMTEXT 로 넓힌다. 전부 확장이라 롤아웃 중 옛 코드와 호환된다.
ALTER TABLE community_post
    ADD COLUMN body_format VARCHAR(10) NOT NULL DEFAULT 'PLAIN' AFTER body,
    ADD COLUMN preview VARCHAR(300) NULL AFTER body_format,
    MODIFY COLUMN body MEDIUMTEXT NOT NULL;
