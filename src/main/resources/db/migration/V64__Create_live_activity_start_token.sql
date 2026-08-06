-- iOS push-to-start 토큰 (iOS 17.2+).
--
-- live_activity_token 과 다른 체계라 테이블을 나눈다:
--   - 그쪽은 카드 하나에 붙는 액티비티 단위 토큰이고 match_id 가 있다.
--   - 이쪽은 앱 단위 토큰이라 특정 경기에 매이지 않는다. 카드가 없어도 발급된다.
--     (Activity.pushToStartTokenUpdates)
--
-- 이 토큰으로는 서버가 카드를 "만들" 수 있다. 지금까지는 앱이 실행돼야만 카드가 떴다.
CREATE TABLE live_activity_start_token (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id  BIGINT       NOT NULL,
    push_token VARCHAR(512) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_live_activity_start_token_push_token UNIQUE (push_token),
    CONSTRAINT fk_live_activity_start_token_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    INDEX idx_live_activity_start_token_member_active (member_id, active)
);
