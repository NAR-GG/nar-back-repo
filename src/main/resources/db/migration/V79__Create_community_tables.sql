-- 커뮤니티 1차 테이블 일괄 생성. 설계와 결정 근거는 docs/community-backend-design.md.
--
-- 전부 CREATE + 컬럼 추가(INSTANT)라 후방호환 — 롤아웃 중 옛 코드가 만나도 무해하다.
-- 투표·제재 테이블은 API 가 나중에 붙지만(작업 순서 6·7) 지금 같이 만든다 — 있어도 무해하고,
-- 마이그레이션을 쪼개 봐야 리뷰만 흩어진다.
--
-- 공통 결정 셋:
--   1) member_id FK 는 "콘텐츠"와 "행위 기록"을 다르게 간다.
--      - 글·댓글(콘텐츠): ON DELETE SET NULL — 회원이 하드 삭제돼도(백오피스
--        MemberDeleteService) 글과 남의 댓글·대화 맥락은 남는다. 화면은 "탈퇴한 사용자".
--        CASCADE 로 걸면 헤비 포스터 삭제 한 방에 남의 댓글까지 한 트랜잭션에서 전멸한다.
--      - 좋아요·투표·스크랩·차단·신고(행위 기록): ON DELETE CASCADE — 지워져도 맥락 파괴가
--        없다. 카운터(like_count 등)가 그만큼 안 줄어 드리프트가 남는데, 회원 삭제는 드물어
--        감수한다. 거슬리면 재집계 배치를 그때 붙인다.
--   2) 정렬·커서는 전부 id 기준. id 가 곧 시간순이라 created_at 정렬처럼 같은 밀리초에서
--      순서가 흔들리지 않고, (선행컬럼, id DESC) 인덱스 하나로 정렬·커서를 같이 처리한다.
--   3) 목록에 항상 보이는 숫자(view/like/comment/vote count)는 역정규화 컬럼.
--      집계 테이블 COUNT 로 목록을 그리면 페이지당 20번 집계라 바로 무너진다.

-- 이름 충돌 정리 — prod 에는 community_post 가 이미 있었다. 외부 커뮤니티(인벤/네이버/OPGG)
-- 크롤링 게시글 테이블로, 자체 커뮤니티와는 무관한 기능이다(app/community/, v3 홈 Top5 위젯).
-- 앞으로의 "진짜" 커뮤니티가 community_* 이름을 갖도록 크롤러 쪽을 개명한다(데이터 보존).
-- RENAME 은 원래 후방호환 위반이지만 여기서만 감수한다 — 롤아웃 겹침 몇 분간 구 파드의
-- 크롤러 insert 와 홈 위젯 조회가 에러날 뿐이고(크롤러는 어차피 7/31 이후 파싱 실패로
-- 3주째 빈손), 새 community_post 와 스키마가 달라 잘못 꽂힐 수도 없다.
RENAME TABLE community_post TO crawled_community_post;

-- 게시글. board_team_id NULL = 전체 게시판(가짜 팀 행을 만들면 온보딩·구독 조인에 새어 나간다).
-- author_team_id 는 작성 시점 응원팀을 굳혀 저장 — favorite_team_id 조인으로 그리면
-- 팀을 옮기는 순간 과거 글 로고가 전부 뒤집힌다.
-- edited_at 은 "수정됨" 판정 전용이다. updated_at 은 ON UPDATE 자동이라 블라인드 같은
-- status 변경에도 튀어서, updated_at != created_at 판정은 수정 안 한 글에 "수정됨"을 띄운다.
CREATE TABLE community_post (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_team_id   BIGINT NULL,
    member_id       BIGINT NULL,
    author_team_id  BIGINT NULL,
    title           VARCHAR(100) NOT NULL,
    body            TEXT NOT NULL,
    view_count      INT NOT NULL DEFAULT 0,
    like_count      INT NOT NULL DEFAULT 0,
    comment_count   INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',  -- VISIBLE/HIDDEN/DELETED
    edited_at       DATETIME(3) NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_community_post_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE SET NULL,
    CONSTRAINT fk_community_post_board_team
        FOREIGN KEY (board_team_id) REFERENCES teams (team_id),
    INDEX idx_community_post_board_id (board_team_id, id DESC),
    -- "내가 쓴 글" + 도배 간격 검사(마지막 작성 시각)가 이 인덱스로 돈다.
    INDEX idx_community_post_member (member_id, id DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 댓글. 깊이는 앱이 1단으로 제한 — 답글의 답글은 parent_id 를 조부모로 올려붙이고
-- mention_member_id 로 대상을 표시한다. depth 컬럼·재귀 CTE 불필요.
-- mention_member_id 는 FK 없는 소프트 참조 — 대상이 탈퇴하면 조회 실패로 "탈퇴한 사용자" 처리.
CREATE TABLE community_comment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id         BIGINT NOT NULL,
    parent_id       BIGINT NULL,
    member_id       BIGINT NULL,
    author_team_id  BIGINT NULL,
    mention_member_id BIGINT NULL,
    body            VARCHAR(1000) NOT NULL,
    like_count      INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_community_comment_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_comment_parent
        FOREIGN KEY (parent_id) REFERENCES community_comment (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_comment_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE SET NULL,
    INDEX idx_community_comment_post (post_id, id),
    INDEX idx_community_comment_parent (parent_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 첨부 사진. status 를 글과 별도로 둔 것은 법적 요구 — 불법촬영물 신고 시 글 전체가 아니라
-- 그 이미지만 즉시 내려야 하는 경우가 있다(전기통신사업법 22조의5 제1항).
CREATE TABLE community_post_image (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_community_post_image_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    INDEX idx_community_post_image_post (post_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 투표. 글당 하나(uk). total_votes 역정규화 — 목록·상세에서 항상 보이는 숫자.
CREATE TABLE community_poll (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id               BIGINT NOT NULL,
    question              VARCHAR(200) NOT NULL,
    hide_results_until_voted BOOLEAN NOT NULL DEFAULT TRUE,
    total_votes           INT NOT NULL DEFAULT 0,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_poll_post UNIQUE (post_id),
    CONSTRAINT fk_community_poll_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE community_poll_option (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id     BIGINT NOT NULL,
    label       VARCHAR(100) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    vote_count  INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_community_poll_option_poll
        FOREIGN KEY (poll_id) REFERENCES community_poll (id) ON DELETE CASCADE,
    INDEX idx_community_poll_option_poll (poll_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- uk (poll_id, member_id) 가 단일 선택을 DB 레벨에서 보장. 복수 선택을 열 땐
-- (poll_id, member_id, option_id) 로 넓히면 되므로 막다른 길이 아니다.
-- poll_id 에 FK 를 안 건 이유 — 삭제 연쇄는 poll → option → vote 의 option FK 로 이미
-- 흐르고, "옵션이 그 투표 소속인가"는 서버가 검증한다. FK 하나 줄여 insert 부모 락을 던다.
CREATE TABLE community_poll_vote (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id     BIGINT NOT NULL,
    option_id   BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_poll_vote UNIQUE (poll_id, member_id),
    CONSTRAINT fk_community_poll_vote_option
        FOREIGN KEY (option_id) REFERENCES community_poll_option (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_poll_vote_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 좋아요 둘 다 uk 만 둔다. "이 20개 글 중 내가 누른 것"은 uk 점조회 20번으로 커버되고,
-- "내가 좋아요한 목록" 화면은 없다(설계 초안의 member 선행 인덱스는 그래서 뺐다).
CREATE TABLE community_post_like (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_post_like UNIQUE (post_id, member_id),
    CONSTRAINT fk_community_post_like_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_post_like_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE community_comment_like (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    comment_id  BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_comment_like UNIQUE (comment_id, member_id),
    CONSTRAINT fk_community_comment_like_comment
        FOREIGN KEY (comment_id) REFERENCES community_comment (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_comment_like_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 스크랩. "내 스크랩" 화면이 member 선행 인덱스로만 도는 화면이라 여기만 member 인덱스가
-- 있고, post_id 를 꼬리에 붙여 커버링 — 목록 페이지의 post_id 추출이 인덱스만으로 끝난다.
CREATE TABLE community_scrap (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_scrap UNIQUE (post_id, member_id),
    CONSTRAINT fk_community_scrap_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_scrap_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    INDEX idx_community_scrap_member (member_id, id DESC, post_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 신고. target_type + target_id 는 다형 참조라 FK 불가 — 대상 실존·VISIBLE 검증은 서버가 한다.
-- 설계 초안의 idx_community_report_target 은 뺐다 — uk 의 (target_type, target_id) prefix 가
-- 같은 조회를 이미 커버한다(대상당 신고는 많아야 수십 행이라 status 잔여 필터도 공짜).
-- idx_community_report_queue (status, created_at) 가 백오피스 신고 큐(PENDING 오래된 순).
CREATE TABLE community_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type     VARCHAR(20) NOT NULL,   -- POST / COMMENT / IMAGE
    target_id       BIGINT NOT NULL,
    reporter_id     BIGINT NOT NULL,
    reason          VARCHAR(30) NOT NULL,   -- ABUSE/OBSCENE/AD/FRAUD/SPAM/ETC
    detail          VARCHAR(200) NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/ACCEPTED/REJECTED
    handled_by      BIGINT NULL,
    handled_at      DATETIME(3) NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_report_once
        UNIQUE (target_type, target_id, reporter_id),
    CONSTRAINT fk_community_report_reporter
        FOREIGN KEY (reporter_id) REFERENCES member (id) ON DELETE CASCADE,
    INDEX idx_community_report_queue (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 차단. uk 가 곧 조회 인덱스("내가 차단한 사람"). blocked_member_id 선행 인덱스는 안 만든다 —
-- "나를 차단한 사람" 조회는 어떤 화면에도 안 쓴다(써서도 안 된다).
CREATE TABLE member_block (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id         BIGINT NOT NULL,
    blocked_member_id BIGINT NOT NULL,
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_member_block UNIQUE (member_id, blocked_member_id),
    CONSTRAINT fk_member_block_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_block_blocked
        FOREIGN KEY (blocked_member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 쓰기 제재(1회 경고 / 2회 7일 / 3회 30일). 규칙 문서가 제재 단계를 이미 앱에 노출했으므로
-- 이 테이블이 없으면 그 문구가 지킬 수 없는 약속이 된다. reason 은 쓰기 거부 응답에 실어
-- "왜 제한됐는지"를 처음부터 보여준다(에브리타임이 뒤늦게 붙인 교훈).
CREATE TABLE member_community_restriction (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT NOT NULL,
    reason      VARCHAR(200) NOT NULL,
    until       DATETIME(3) NULL,           -- NULL = 영구
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_member_community_restriction_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    INDEX idx_member_community_restriction_member (member_id, until)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 응원팀 변경 시각. 팀 게시판 쓰기 30일 쿨다운(D-1) 판정용. NULL = 변경 이력 없음(쿨다운 없음).
-- nullable 컬럼 추가라 MySQL 8.4 에서 INSTANT — 메타데이터 락 찰나.
ALTER TABLE member ADD COLUMN favorite_team_changed_at DATETIME(3) NULL;
