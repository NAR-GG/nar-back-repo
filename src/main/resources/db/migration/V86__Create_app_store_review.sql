-- 앱스토어 고객 리뷰 발송 dedupe.
--
-- 애플에는 리뷰 웹훅이 없다 — App Store Connect 웹훅 이벤트는 빌드 업로드·베타 빌드·앱 버전
-- 상태·에셋팩·TestFlight 피드백 5종뿐이고 리뷰/평점은 없다. 그래서 리뷰만 폴링으로 당겨온다.
--
-- 폴링은 같은 리뷰를 매번 다시 주므로 발송한 리뷰 id 를 남겨 INSERT IGNORE 로 걸러낸다.
-- 워터마크(마지막 createdDate) 한 줄 대신 id 표를 쓰는 이유: 같은 초에 들어온 리뷰의 tie 와
-- 시계 역전에 걸리지 않고, "언제 뭘 보냈나" 이력이 공짜로 남는다.
CREATE TABLE app_store_review (
    -- 애플은 UUID, 구글 플레이는 `gp:AOqpTO...` 형태로 길다. 둘 다 들어가게 넉넉히 잡는다.
    review_id   VARCHAR(191) NOT NULL,
    -- IOS | ANDROID. 구글 플레이 확장 때 마이그레이션 없이 같은 표를 쓴다.
    platform    VARCHAR(16)  NOT NULL,
    rating      TINYINT      NULL,
    territory   VARCHAR(16)  NULL,
    notified_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (platform, review_id)
);
