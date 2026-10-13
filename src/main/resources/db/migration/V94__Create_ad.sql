-- 앱 광고 배너. 백오피스에서 등록하고 앱 홈·경기 리스트·경기 상세에 노출한다.
-- 노출 조건: active 이고 now 가 [start_at, end_at) 안. NULL 은 무제한. 해당 자리에 광고가 없으면 앱이 광고주 모집 배너를 둔다.
-- 후방호환: 신규 테이블이라 롤아웃 중 옛 코드와 충돌 없다.
CREATE TABLE IF NOT EXISTS ad (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title      VARCHAR(100) NOT NULL,
    placement  VARCHAR(20)  NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    link_url   VARCHAR(500) NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    start_at   DATETIME     NULL,
    end_at     DATETIME     NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    INDEX idx_ad_placement (placement, active)
);
