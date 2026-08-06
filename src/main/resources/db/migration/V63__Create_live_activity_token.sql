-- iOS Live Activity(잠금화면 실시간 경기 카드) 갱신용 ActivityKit 푸시 토큰.
--
-- member_device 의 FCM 등록 토큰과 다른 체계라 별도 테이블로 둔다:
--   - 발급자가 APNs 다. FCM 은 이 토큰으로 못 보낸다.
--   - 기기가 아니라 액티비티(카드) 하나에 붙는다. 같은 기기가 경기마다 다른 토큰을 갖는다.
--   - 카드가 끝나면 죽는다. 수명이 기기 등록보다 훨씬 짧다.
CREATE TABLE live_activity_token (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id   VARCHAR(64)  NOT NULL,
    push_token VARCHAR(512) NOT NULL,
    member_id  BIGINT       NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_live_activity_token_push_token UNIQUE (push_token),
    CONSTRAINT fk_live_activity_token_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    INDEX idx_live_activity_token_match_active (match_id, active)
);
