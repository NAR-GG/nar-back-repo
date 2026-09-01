-- 앱 마켓 알림 dedupe 표 둘. 애플과 구글 플레이가 같은 표를 platform 으로 나눠 쓴다.
--
-- 왜 폴링이 필요한가:
--   애플 — 웹훅 이벤트 5종(빌드 업로드·베타 빌드·앱 버전 상태·에셋팩·TestFlight 피드백)에
--          리뷰/평점이 없다. 그래서 리뷰만 폴링한다. 심사·배포는 웹훅으로 받아 dedupe 가 필요 없다.
--   플레이 — 웹훅이 아예 없다. RTDN(Pub/Sub)은 결제·구독 전용이다. 리뷰도 출시도 폴링이다.
--
-- 폴링은 같은 것을 매번 다시 주므로, 발송한 것의 키를 남겨 INSERT IGNORE 로 걸러낸다.
-- 워터마크(마지막 시각) 한 줄 대신 키 표를 쓰는 이유: 같은 초에 들어온 항목의 tie 와
-- 시계 역전에 걸리지 않고, "언제 뭘 보냈나" 이력이 공짜로 남는다.

CREATE TABLE store_review_notified (
    -- 애플은 UUID, 구글 플레이는 `gp:AOqpTO...` 형태로 길다. 둘 다 들어가게 넉넉히 잡는다.
    review_id   VARCHAR(191) NOT NULL,
    -- IOS | ANDROID
    platform    VARCHAR(16)  NOT NULL,
    rating      TINYINT      NULL,
    territory   VARCHAR(16)  NULL,
    notified_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (platform, review_id)
);

-- 출시 알림 dedupe. 플레이 전용이다 — 애플은 웹훅이라 여기 안 들른다.
--
-- 키에 status 를 넣는다. 롤아웃은 inProgress → completed 로 옮겨가므로 (버전, 상태) 쌍마다
-- 한 번씩 알려야 "출시 시작"과 "출시 완료"가 둘 다 나온다. 버전만 키로 잡으면 시작만 오고 끝난다.
CREATE TABLE store_release_notified (
    platform     VARCHAR(16) NOT NULL,
    version_code VARCHAR(32) NOT NULL,
    status       VARCHAR(32) NOT NULL,
    notified_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (platform, version_code, status)
);
