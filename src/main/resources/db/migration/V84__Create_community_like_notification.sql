-- 좋아요 알림 dedupe — (글 × 누른 사람) 최초 1회만 발송한다(인스타 방식).
-- community_post_like 행은 취소 시 지워져서 재좋아요를 구분 못 하므로,
-- 발송 기록을 따로 남기고 취소해도 지우지 않는다. 좋아요↔취소 반복 도배 방지.
CREATE TABLE community_like_notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_like_notification UNIQUE (post_id, member_id),
    CONSTRAINT fk_community_like_notification_post
        FOREIGN KEY (post_id) REFERENCES community_post(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_like_notification_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
);
