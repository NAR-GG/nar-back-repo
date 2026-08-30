-- 글 단위 알림 끄기(유튜브·레딧의 per-post mute). 행 존재 = 그 회원이 그 글에서
-- 오는 커뮤니티 알림(댓글·답글)을 받지 않는다. 기본은 수신이라 mute 를 예외로
-- 저장한다 — 반대로 구독을 저장하면 글 쓸 때마다 행을 만들어야 한다.
-- uk 가 곧 조회 인덱스(수신자별 판정: member_id 선행이 아니라 post 선행인 이유 —
-- 발송 판정이 (post_id, member_id) 점조회 한 번이고, "내가 끈 글 목록" 화면은 없다).
CREATE TABLE community_post_notification_mute (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_post_notification_mute UNIQUE (post_id, member_id),
    CONSTRAINT fk_community_post_notification_mute_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_post_notification_mute_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
