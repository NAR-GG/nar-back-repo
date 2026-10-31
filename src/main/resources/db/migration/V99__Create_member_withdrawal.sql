-- 탈퇴 기록. 탈퇴는 member 행을 지우고 FK CASCADE 로 20여 개 테이블을 함께 지우는 하드 딜리트라
-- 지금까지는 탈퇴 사실 자체가 남지 않았다(탈퇴 수·가입 후 이탈 기간을 셀 수 없고, 제재받은 회원이
-- 탈퇴 후 같은 소셜 계정으로 재가입하면 제재가 사라진다). 하드 딜리트는 그대로 두고 지우기 직전에 한 줄 남긴다.
--
-- social_id_hashes: 'PROVIDER:providerId' 의 SHA-256(hex) 를 콤마로 이은 목록(회원당 소셜 1~3개).
--   재가입 대조용이라 30일 뒤(제재 중이면 제재 만료 뒤) NULL 로 비운다 — 그 뒤엔 개인을 특정할 수 없는 통계 행만 남는다.
-- member_id: 원래 회원 id. FK 가 아니다(회원 행은 지워진다). 연타 탈퇴의 중복 기록을 UNIQUE 로 막는다.
-- restricted_until: 탈퇴 시점에 유효한 커뮤니티 제재 만료. 영구 제재는 9999-12-31. 제재 없으면 NULL.
-- reason: 탈퇴 사유. 앱이 아직 보내지 않아 NULL — 화면이 생기면 채운다.
CREATE TABLE member_withdrawal (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id         BIGINT       NOT NULL,
    providers         VARCHAR(100) NOT NULL,
    social_id_hashes  VARCHAR(600) NULL,
    joined_at         DATETIME     NOT NULL,
    onboarded_at      DATETIME     NULL,
    withdrawn_at      DATETIME     NOT NULL,
    restricted_until  DATETIME(3)  NULL,         -- member_community_restriction.until 과 같은 정밀도
    reason            VARCHAR(200) NULL,
    UNIQUE KEY uk_member_withdrawal_member (member_id),
    INDEX idx_member_withdrawal_withdrawn_at (withdrawn_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
