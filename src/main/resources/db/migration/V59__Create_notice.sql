-- 공지사항: 백오피스에서 작성하고 앱(마이페이지 목록·캘린더 상단 띠배너)에 노출한다.
-- published_at NULL = 임시저장(앱 미노출), promote_until = 띠배너 노출 종료 시각(NULL = 배너 미노출).
CREATE TABLE IF NOT EXISTS notice (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    content       MEDIUMTEXT   NOT NULL,
    pinned        BOOLEAN      NOT NULL DEFAULT FALSE,
    promote_until DATETIME     NULL,
    published_at  DATETIME     NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL
);
