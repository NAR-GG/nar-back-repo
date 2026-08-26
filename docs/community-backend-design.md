# 커뮤니티 백엔드 설계 — 검토용

앱(Flutter) 1차는 화면과 인터랙션까지 정적 더미로 끝났다
(`warding-mobile-repo` PR #215). 이 문서는 그걸 실제로 굴리기 위해 백엔드에서
만들 것과, **결정이 필요한 지점**을 정리한다.

규칙 원문은 `warding-mobile-repo/docs/community-policy.md`.

## 전제 — 지금 규모


| 항목                   | 값                     |
| -------------------- | --------------------- |
| `member`             | 6,497 행               |
| `league_match`       | 3,356 행               |
| `live_player_rating` | 2,981 행               |
| MySQL                | 8.4.11 (맥미니 호스트 네이티브) |
| 웹 파드                 | replicas 1            |
|                      |                       |


**이 숫자가 이 문서의 거의 모든 판단을 좌우한다.** 회원 6.5천 명이면 게시글이
쌓여도 수만~수십만 행이다. 이 구간에서는 인덱스만 제대로 걸면 단순한 구조가
가장 빠르고, 캐시·샤딩·검색엔진은 전부 손해다. 아래에서 "지금은 하지 말자"는
말이 반복되는데 근거는 다 이 표다.

동시에 **replicas 1** 이라 인메모리 캐시를 쓰면 파드 교체마다 날아간다. 상태는
DB 에 두는 쪽이 단순하다.

---

## 1. 새로 만들 테이블

> 이 장의 SQL 은 **초안**이다. 확정 DDL 은 `V79__Create_community_tables.sql` 이
> 단일 진실이고, 검토에서 바뀐 점(SET NULL, edited_at, 인덱스 조정)은 7장에 있다.

기존 관례를 따른다 — `BIGINT AUTO_INCREMENT PRIMARY KEY`, `DATETIME(3)`,
`created_at DEFAULT CURRENT_TIMESTAMP(3)`, FK 는 `ON DELETE CASCADE`,
인덱스 이름은 `idx_<table>_<컬럼>`, 유니크는 `uk_<table>_<의미>`.

### 1-1. `community_post` — 게시글

```sql
CREATE TABLE community_post (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_team_id   BIGINT NULL,           -- NULL = 전체 게시판, 값 = 팀 게시판
    member_id       BIGINT NOT NULL,
    author_team_id  BIGINT NULL,           -- 작성 시점 응원팀 (아래 설명)
    title           VARCHAR(100) NOT NULL,
    body            TEXT NOT NULL,
    view_count      INT NOT NULL DEFAULT 0,
    like_count      INT NOT NULL DEFAULT 0,
    comment_count   INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',  -- VISIBLE/HIDDEN/DELETED
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_community_post_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_post_board_team
        FOREIGN KEY (board_team_id) REFERENCES teams(team_id),
    INDEX idx_community_post_board_id (board_team_id, id DESC),
    INDEX idx_community_post_member (member_id, id DESC)
);
```

`**board_team_id` 를 NULL 로 전체 게시판을 표현하는 이유** — 전체 게시판을 위한
가짜 팀 행을 `teams` 에 만들면 그 행이 온보딩 팀 목록·구독·경기 조인에 전부
새어 나간다. 게시판 목록은 어차피 "전체 + 팀 전부"라 별도 `community_board`
테이블도 필요 없다. 팀이 추가되면 게시판이 자동으로 생긴다.

`**author_team_id` 는 작성 시점 응원팀을 굳혀 저장한다.** `member.favorite_team_id`
를 조인해 그리면 **유저가 팀을 옮기는 순간 과거 글 전부가 새 팀 로고로 뒤집혀**
문맥이 깨진다. 앱이 이미 이 전제로 만들어져 있다.

`**status` 는 소프트 삭제다.** 하드 삭제하면 그 글에 달린 남의 댓글이 같이
날아가고, 신고 처리 이력에서 "무엇을 지웠는지"가 사라진다.

**정렬 인덱스가 `(board_team_id, id DESC)` 인 이유** — 커뮤니티 목록은 항상
최신순이고, `id` 가 곧 시간순이다. `created_at` 으로 정렬하면 같은 밀리초에
들어온 글의 순서가 흔들려 커서 페이지네이션이 깨진다.

### 1-2. `community_comment` — 댓글·답글

```sql
CREATE TABLE community_comment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id         BIGINT NOT NULL,
    parent_id       BIGINT NULL,           -- NULL = 최상위, 값 = 답글 (1단까지만)
    member_id       BIGINT NOT NULL,
    author_team_id  BIGINT NULL,
    mention_member_id BIGINT NULL,         -- @닉네임 대상
    body            VARCHAR(1000) NOT NULL,
    like_count      INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_community_comment_post
        FOREIGN KEY (post_id) REFERENCES community_post(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_comment_parent
        FOREIGN KEY (parent_id) REFERENCES community_comment(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_comment_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    INDEX idx_community_comment_post (post_id, id),
    INDEX idx_community_comment_parent (parent_id, id)
);
```

**깊이는 앱이 1단으로 제한한다.** 답글의 답글은 `parent_id` 를 조부모로
올려붙이고 `mention_member_id` 로 대상을 표시한다. DB 에 depth 컬럼을 두거나
재귀 CTE 를 쓸 이유가 없다.

### 1-3. `community_post_image` — 첨부 사진

```sql
CREATE TABLE community_post_image (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',  -- 이미지 단독 블라인드용
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_community_post_image_post
        FOREIGN KEY (post_id) REFERENCES community_post(id) ON DELETE CASCADE,
    INDEX idx_community_post_image_post (post_id, sort_order)
);
```

업로드는 프로필 사진과 같은 경로를 쓴다 — 백엔드가 Cloudinary 서명을 주고
앱이 직접 올린 뒤 `secure_url` 을 보낸다(`ProfileImageRepository` 참고).
**이미지에 `status` 를 따로 둔 것은 법적 요구다.** 불법촬영물 신고를 받으면
글 전체가 아니라 그 이미지만 즉시 내려야 하는 경우가 있다.

### 1-4. `community_poll` / `community_poll_option` / `community_poll_vote`

```sql
CREATE TABLE community_poll (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id               BIGINT NOT NULL,
    question              VARCHAR(200) NOT NULL,
    hide_results_until_voted BOOLEAN NOT NULL DEFAULT TRUE,
    total_votes           INT NOT NULL DEFAULT 0,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_poll_post UNIQUE (post_id),
    CONSTRAINT fk_community_poll_post
        FOREIGN KEY (post_id) REFERENCES community_post(id) ON DELETE CASCADE
);

CREATE TABLE community_poll_option (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id     BIGINT NOT NULL,
    label       VARCHAR(100) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    vote_count  INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_community_poll_option_poll
        FOREIGN KEY (poll_id) REFERENCES community_poll(id) ON DELETE CASCADE,
    INDEX idx_community_poll_option_poll (poll_id, sort_order)
);

CREATE TABLE community_poll_vote (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id     BIGINT NOT NULL,
    option_id   BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_poll_vote UNIQUE (poll_id, member_id),  -- 단일 선택
    CONSTRAINT fk_community_poll_vote_option
        FOREIGN KEY (option_id) REFERENCES community_poll_option(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_poll_vote_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
);
```

`**uk (poll_id, member_id)` 가 "단일 선택"을 DB 레벨에서 보장한다.** 나중에
복수 선택을 열려면 이 유니크를 `(poll_id, member_id, option_id)` 로 넓히면
되므로 지금 단일로 잠가도 막다른 길이 아니다.

### 1-5. `community_post_like` / `community_comment_like` / `community_scrap`

셋 다 같은 모양이다.

```sql
CREATE TABLE community_post_like (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_post_like UNIQUE (post_id, member_id),
    CONSTRAINT fk_community_post_like_post
        FOREIGN KEY (post_id) REFERENCES community_post(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_post_like_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    INDEX idx_community_post_like_member (member_id, id DESC)
);
```

`community_scrap` 은 여기에 `**member_id` 선행 인덱스가 특히 중요**하다. "내
스크랩 목록"이 그 인덱스로만 도는 화면이다.

### 1-6. `community_report` — 신고

```sql
CREATE TABLE community_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type     VARCHAR(20) NOT NULL,   -- POST / COMMENT / IMAGE
    target_id       BIGINT NOT NULL,
    reporter_id     BIGINT NOT NULL,
    reason          VARCHAR(30) NOT NULL,   -- ABUSE/OBSCENE/AD/FRAUD/SPAM/ETC
    detail          VARCHAR(200) NULL,      -- ETC 일 때 필수
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/ACCEPTED/REJECTED
    handled_by      BIGINT NULL,
    handled_at      DATETIME(3) NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_report_once
        UNIQUE (target_type, target_id, reporter_id),   -- 같은 대상 중복 신고 차단
    CONSTRAINT fk_community_report_reporter
        FOREIGN KEY (reporter_id) REFERENCES member(id) ON DELETE CASCADE,
    INDEX idx_community_report_queue (status, created_at),
    INDEX idx_community_report_target (target_type, target_id)
);
```

`target_type` + `target_id` 는 다형 참조라 FK 를 걸 수 없다. 대신
`idx_community_report_target` 으로 "이 글이 몇 번 신고됐나"를 즉시 센다.

`**idx_community_report_queue (status, created_at)` 가 백오피스 신고 큐를 굴리는
인덱스다.** PENDING 만 오래된 순으로 뽑는 게 운영의 기본 동선이다.

### 1-7. `member_block` — 사용자 차단

```sql
CREATE TABLE member_block (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id       BIGINT NOT NULL,        -- 차단한 사람
    blocked_member_id BIGINT NOT NULL,      -- 차단당한 사람
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_member_block UNIQUE (member_id, blocked_member_id),
    CONSTRAINT fk_member_block_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_block_blocked
        FOREIGN KEY (blocked_member_id) REFERENCES member(id) ON DELETE CASCADE
);
```

`uk (member_id, blocked_member_id)` 가 곧 조회 인덱스다. 별도 인덱스 불필요.

### 1-8. (선택) `member_community_restriction` — 쓰기 제재

제재 단계(1회 경고 / 2회 7일 / 3회 30일)를 실제로 굴리려면 필요하다.

```sql
CREATE TABLE member_community_restriction (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT NOT NULL,
    reason      VARCHAR(200) NOT NULL,
    until       DATETIME(3) NULL,           -- NULL = 영구
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_member_community_restriction_member
        FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    INDEX idx_member_community_restriction_member (member_id, until)
);
```

**규칙 문서에 제재 단계를 이미 써서 앱에 노출했다.** 이 테이블이 없으면 그
문구가 지킬 수 없는 약속이 된다.

---

## 2. 조회수 — 역정규화할 것인가

### 결론: 한다. `community_post.view_count` 컬럼.

대안은 `community_post_view` 테이블에 조회 이벤트를 쌓고 `COUNT(*)` 하는 것인데,
**목록 한 페이지(20개)를 그리려고 조회 이벤트 테이블을 20번 집계해야 한다.**
글이 3만 개면 뷰 이벤트는 수백만 행이고, 목록 화면이 바로 무너진다.

같은 이유로 `like_count`, `comment_count` 도 컬럼으로 둔다. 목록에 항상 보이는
숫자는 전부 역정규화한다.

### 정합성은 어떻게 지키나

- `like_count` / `comment_count` — 좋아요·댓글은 **같은 트랜잭션 안에서**
행 삽입과 카운터 증감을 함께 한다. 초당 수십 건 수준이라 락 경합이 없다.
- `view_count` — 트랜잭션에 넣지 않는다. 조회는 쓰기보다 수십 배 잦고, 여기에
락을 걸면 인기 글에서 경합이 생긴다. **별도 커넥션에서 `UPDATE ... SET view_count = view_count + 1` 만 쏘고 실패해도 무시한다.** 조회수는 틀려도
아무도 안 죽는 숫자다.
- 주기적 보정 — 필요해지면 하루 한 번 실제 카운트로 맞추는 배치를 붙인다.
지금 규모에서는 아직 필요 없다.

### 중복 조회를 막을 것인가 — **결정 필요 (아래 D-4)**

---

## 3. 차단 — 풀스캔 없이 거르는 법

이 문서에서 가장 조심해야 할 부분이다.

### 문제

"차단한 사용자의 글·댓글이 안 보인다"를 목록 쿼리에 어떻게 넣느냐다. 순진하게
쓰면 이렇게 된다.

```sql
-- ❌ 이렇게 쓰면 안 된다
SELECT * FROM community_post p
WHERE p.board_team_id <=> ?
  AND p.member_id NOT IN (SELECT blocked_member_id FROM member_block WHERE member_id = ?)
ORDER BY p.created_at DESC
LIMIT 20 OFFSET 2000;
```

두 군데가 깨진다.

1. `**OFFSET**` — 2000번째 페이지를 보려면 2020행을 읽고 2000행을 버린다.
 페이지가 깊어질수록 선형으로 느려진다. 차단과 무관하게 이건 그냥 틀렸다.
2. `**NOT IN (서브쿼리)**` — 옵티마이저가 매 행마다 서브쿼리를 다시 볼 수도,
 임시 테이블로 물릴 수도 있다. 실행 계획이 데이터에 따라 바뀐다.

### 권장안 — 커서 페이지네이션 + 차단 목록을 파라미터로 내려꽂기

**차단 목록은 작다.** 사람이 손으로 차단하는 수라 보통 0~수십 개다. 그러면
서브쿼리로 둘 이유가 없다.

```java
// 1) 차단 목록을 먼저 한 번 읽는다. uk_member_block 로 인덱스 조회, 수십 행.
List<Long> blocked = memberBlockRepository.findBlockedIds(memberId);

// 2) 목록은 커서 기반. blocked 를 IN 파라미터로 박는다.
```

```sql
SELECT p.* FROM community_post p
WHERE p.board_team_id <=> :boardTeamId
  AND p.status = 'VISIBLE'
  AND p.id < :cursorId                      -- 첫 페이지는 생략
  AND (:blockedCount = 0 OR p.member_id NOT IN (:blockedIds))
ORDER BY p.id DESC
LIMIT :size;
```

**왜 빠른가**

- `idx_community_post_board_id (board_team_id, id DESC)` 가 정렬과 커서를 동시에
처리한다. `id < cursor` 는 인덱스 범위 스캔의 시작점일 뿐이라 **몇 번째
페이지든 비용이 같다.**
- `NOT IN (리터럴 목록)` 은 인덱스로 거를 수 없는 잔여 조건이지만, 이미 20행
근처만 읽고 있으므로 **거기서 몇 개 빼는 비용은 무시할 수 있다.**
- 차단 목록 조회는 유니크 인덱스 조회 한 번이다.

**주의 하나** — 차단한 사람의 글이 그 게시판을 도배한 경우, 20개를 읽어 15개가
걸러지면 페이지가 5개짜리로 나온다. 대응은 `**LIMIT` 을 넉넉히(예: size + 차단
수, 또는 size × 2) 잡고 애플리케이션에서 size 만큼 자르는 것**이다. 부족하면 한
번 더 읽는다. 이 방식이 안전한 이유는 커서 페이지네이션이라 **이어 읽는 비용이
첫 페이지와 같기 때문**이다.

### 차단 목록이 아주 커지면

수백 개를 넘어가면 `IN` 목록이 길어져 파싱·플랜 캐시에 부담이 된다. 그때는
**안티 조인**으로 바꾼다.

```sql
SELECT p.* FROM community_post p
LEFT JOIN member_block b
       ON b.member_id = :memberId AND b.blocked_member_id = p.member_id
WHERE p.board_team_id <=> :boardTeamId
  AND p.status = 'VISIBLE'
  AND p.id < :cursorId
  AND b.id IS NULL
ORDER BY p.id DESC
LIMIT :size;
```

`uk_member_block (member_id, blocked_member_id)` 로 행마다 인덱스 한 번씩 친다.
읽는 행이 20개 근처라 20번의 인덱스 조회이고, 이것도 싸다.

**지금은 `IN` 목록으로 시작하고, 차단 수가 커지면 안티 조인으로 바꾸면 된다.**
쿼리 한 줄 교체라 미리 복잡하게 갈 이유가 없다.

### 하지 말아야 할 것

- **앱에서 거르기** — 서버가 20개를 주고 앱이 15개를 지우면 화면이 들쭉날쭉하고
페이지네이션이 어긋난다. 무한 스크롤에서 특히 티가 난다.
- **차단당한 사람 기준 인덱스** — `member_block` 에 `blocked_member_id` 선행
인덱스를 추가하고 싶어질 텐데, 조회 방향은 항상 "내가 차단한 사람"이라 필요
없다. "나를 차단한 사람 목록"은 어떤 화면에도 안 쓴다(써서도 안 된다).
- **차단 캐시** — replicas 1 이라 파드 교체마다 날아가고, 원본 조회가 유니크
인덱스 한 번이라 캐시할 가치가 없다.

### 댓글도 같은 방식

댓글은 한 글에 수십~수백 개라 페이지네이션 부담이 더 작다. 같은 `IN` 필터를
쓰되, **차단한 사람의 댓글을 아예 지울지 "차단한 사용자의 댓글입니다"로 자리를
남길지**는 결정 사항이다(D-5).

---

## 4. 결정이 필요한 것

여기부터는 내가 임의로 못 정한다. 각 항목의 ✅ 가 내 추천이다.

### D-1. 응원팀 변경 후 쓰기 쿨다운 — **확정: 30일**

팀을 바꾸면 **30일간 팀 게시판에 못 쓴다.** 읽기와 나머지 앱 기능은 즉시 반영되고,
전체 게시판 쓰기도 막지 않는다. 막히는 건 팀 게시판 쓰기뿐이다.

30일로 잡은 이유는 **LCK 시즌 한 스플릿이 대략 그 길이**라, 시즌 중에 팀을
돌려가며 게시판을 옮겨 다니는 게 사실상 불가능해지기 때문이다. 7일이면 스플릿
안에서 서너 번 갈아탈 수 있어 도배 경로가 덜 막힌다.

구현:

```sql
ALTER TABLE member ADD COLUMN favorite_team_changed_at DATETIME(3) NULL;
```

`favorite_team_id` 를 바꿀 때 같이 찍고, 팀 게시판 쓰기 검사에서
`favorite_team_changed_at IS NULL OR favorite_team_changed_at + INTERVAL 30 DAY < NOW()`
를 본다. 새 테이블은 필요 없다.

**앱에 남은 일** — 잠금 바 문구가 지금은 "OO 팬만 글을 쓸 수 있어요" 하나뿐이다.
쿨다운으로 막힌 경우는 이유가 달라서 문구를 따로 줘야 한다
(예: "응원팀을 바꾼 지 얼마 되지 않았어요. N일 뒤부터 쓸 수 있어요").
API 가 남은 일수를 내려줘야 앱이 그릴 수 있다.

### D-2. 게시글 수정 허용 여부


| 선택지                  | 결과                                                                |
| -------------------- | ----------------------------------------------------------------- |
| ✅ **수정 허용 + 수정됨 표시** | `updated_at != created_at` 이면 앱에 "수정됨". 오타 고치려고 지웠다 다시 쓰는 일이 없어진다 |
| 수정 금지                | 단순하지만, 댓글이 달린 뒤 본문만 바꿔치기하는 악용도 같이 막힌다                             |
| 댓글 달리기 전까지만 수정       | 악용은 막고 오타는 고칠 수 있다. 대신 "왜 수정이 안 되지" 문의가 는다                        |


### D-3. 신고 임계 처리 — **확정: 3건 이상이면 Discord 알림**

자동으로 숨기지 않는다. **3건 이상 신고된 대상이 생기면 Discord 로 쏘고, 사람이
보고 판단한다.**

자동 블라인드를 안 하는 이유는 조직적 신고 때문이다. 회원 6.5천 명 규모에서
3건은 마음먹으면 쉽게 모을 수 있는 수라, 그걸로 글이 자동으로 내려가면 멀쩡한
글을 지우는 도구가 된다. 반대로 임계를 크게 잡으면(예: 20건) 정작 심한 글이
그때까지 노출된다. **알림은 그 딜레마를 피한다** — 빠르게 인지하되 판단은 사람이
한다.

**기존 인프라를 그대로 쓴다.** `NotificationService` 에 Discord 웹훅이 이미 세
개(`notification.discord.webhook-url`, `player-webhook-url`,
`roster-webhook-url`) 붙어 있고 스케줄러 실패·로스터 변동 알림이 이 경로로 간다.
커뮤니티 신고용 웹훅을 하나 더 추가하거나(`community-webhook-url`) 기본 웹훅을
재사용하면 된다. 채널을 나누는 편이 낫다 — 스케줄러 실패 알림에 섞이면 묻힌다.

**언제 쏘나** — 신고가 들어와 그 대상의 누적 신고 수가 **정확히 3이 되는 순간**
한 번만 쏜다. 3건 이후 매번 쏘면 같은 글로 알림이 도배된다.

```sql
-- 신고 저장 직후
SELECT COUNT(*) FROM community_report
WHERE target_type = ? AND target_id = ? AND status = 'PENDING';
-- 결과가 3 이면 Discord 발송
```

`idx_community_report_target (target_type, target_id)` 로 도는 카운트라 비용이
없다.

**단, 이미지는 예외다.** `target_type = IMAGE` 는 임계 없이 1건에 바로 쏜다
(D-7 참고).

알림에 담을 것: 대상 종류·id, 누적 신고 수, 사유 분포, 본문 앞부분, 백오피스
링크. **링크가 없으면 알림을 받고도 손이 안 간다.**

### D-4. 조회수 중복 카운트


| 선택지                    | 결과                                                                                  |
| ---------------------- | ----------------------------------------------------------------------------------- |
| ✅ **중복 허용 (열 때마다 +1)** | 컬럼 하나면 끝. 조회수는 원래 정확한 숫자가 아니고, 앱에도 참고용으로만 보인다                                       |
| 회원당 1회                 | `community_post_view(post_id, member_id)` 테이블이 생긴다. 글 3만 × 회원 6.5천이면 최악의 경우 행이 폭발한다 |
| 세션/기간당 1회              | 중간. Redis 같은 게 필요해지는데 지금 인프라에 없다                                                    |


공지사항이 이미 `noticeViewUrl` 로 중복 허용 방식이다. 맞추는 게 일관된다.

### D-5. 차단한 사용자의 댓글 표시


| 선택지                            | 결과                                                    |
| ------------------------------ | ----------------------------------------------------- |
| ✅ **자리를 남기고 "차단한 사용자의 댓글입니다"** | 대화 맥락이 유지된다. 답글이 달린 댓글이 통째로 사라지면 답글만 떠서 무슨 말인지 알 수 없다 |
| 완전히 숨김                         | 깔끔하지만 위 문제가 생긴다                                       |


글 목록에서는 완전히 숨기는 게 맞다(맥락이 없으니까). **댓글만 다르게 간다.**

### D-6. 규칙 전문을 서버에서 내릴 것인가


| 선택지                                                | 결과                                                  |
| -------------------------------------------------- | --------------------------------------------------- |
| ✅ **서버에서 내린다** (`GET /api/mobile/community/rules`) | 규칙 한 줄 고치는 데 스토어 심사가 필요 없다. 앱은 받은 걸 그리고, 실패하면 내장 폴백 |
| 앱에 상수로 유지                                          | 지금 상태. 규칙 개정마다 앱 배포가 필요하다                           |


규칙에 **제재 단계와 법적 근거를 이미 써서 노출했다.** 이건 개정될 수밖에 없는
문서다. 버전(`rules_version`)도 같이 내려주면 나중에 "동의 받은 버전" 추적도
가능하다.

### D-7. 사진 첨부 — **확정: 1차에 포함, 백오피스 큐는 차후**

사진을 1차에 넣고, 신고 대응은 **Discord 알림 + 사람이 직접 처리**로 간다.
백오피스 신고 큐는 나중에 만든다.

**먼저 앞 문서에서 내가 과장한 것을 정정한다.** "사진을 여는 순간 불법촬영물
신고 처리가 법적 의무가 된다"고 썼는데 정확하지 않다.


| 조항                | 내용                                 | 대상                                       |
| ----------------- | ---------------------------------- | ---------------------------------------- |
| 전기통신사업법 22조의5 제1항 | 신고·삭제요청으로 **인식한 경우** 지체 없이 삭제·접속차단 | 부가통신사업 신고 사업자 전반                         |
| 제2항               | 필터링 등 **기술적·관리적 사전조치**(DNA 매칭 등)   | 시행령이 정한 **사전조치의무사업자** — 매출·이용자 규모 요건이 있다 |


우리가 걸릴 가능성이 있는 건 제1항("신고를 받으면 지워라")이고, 필터링 시스템을
만들라는 제2항은 회원 6.5천 명 규모에서는 해당하지 않을 가능성이 높다.

### 그래서 붙는 조건

큐 없이 가는 대신 아래가 전제다. **이게 안 지켜지면 사진을 여는 결정이
위험해진다.**

#### 1. 이미지 신고는 임계 없이 1건에 즉시 알린다

D-3 의 "3건 누적" 은 **텍스트 기준**이다. 이미지에 그대로 적용하면 안 된다.

- 텍스트는 지우면 끝이지만 **이미지는 그 사이 저장·재유포된다.** 대응 속도가 곧
피해 크기다
- 불법촬영물은 3건이 모일 때까지 기다릴 성질의 것이 아니다
- 이미지 신고는 애초에 드물다. 1건마다 쏴도 알림이 도배되지 않는다

```java
int threshold = (targetType == IMAGE) ? 1 : 3;
```

이미지 신고 알림은 **멘션을 붙여** 다른 알림과 구분되게 한다. 스케줄러 실패
알림 틈에 묻히면 안 된다.

#### 2. 처리 수단이 실제로 있어야 한다

백오피스가 없으니 알림을 받은 사람이 DB 를 직접 고친다. 최소한 이것만은 미리
준비해 둔다.

- `community_post_image.status` 를 `HIDDEN` 으로 바꾸는 SQL 을 손에 들고 있을 것
- 캐시가 있다면 무효화 방법도 같이

**그 SQL 을 알림 메시지에 같이 실어 보내는 것도 방법이다.** 알림을 받고 나서
쿼리를 찾는 시간이 곧 노출 시간이다.

#### 3. 채널을 보는 사람이 정해져야 한다

App Store Guideline 1.2 는 **신고된 콘텐츠를 24시간 내에 처리**할 것을 요구한다.
"Discord 알림이 온다"는 절차가 아니라 도구다. **새벽에 온 알림을 아침에 보는
운영이면 24시간 안에 들어오지만, 아무도 안 보면 절차가 없는 것과 같다.**

심사 제출 시 리뷰 노트에 이 절차를 적는다(`warding-mobile-repo/docs/ community-store-review-risks.md` 참고).

#### 4. 이미지 단독 블라인드

글 전체가 아니라 그 이미지만 내려야 하는 경우가 있다. 그래서
`community_post_image` 에 `status` 를 따로 뒀다. 신고 대상 타입에도
`IMAGE` 가 있어야 한다 — `community_report.target_type` 에 이미 포함했다.

---

## 5. API 표면 (초안)

기존 관례대로 `ApiConfig` 에 상수로 넣는다.

```
GET    /api/mobile/community/posts?boardTeamId=&cursor=&size=   목록 (선택적 인증)
GET    /api/mobile/community/posts/{id}                          상세 (선택적 인증)
POST   /api/mobile/community/posts                               작성 (인증)
PUT    /api/mobile/community/posts/{id}                          수정 (인증, D-2)
DELETE /api/mobile/community/posts/{id}                          삭제 (인증, 소프트)
POST   /api/mobile/community/posts/{id}/view                     조회수 +1
POST   /api/mobile/community/posts/{id}/like                     추천 토글 (인증)
POST   /api/mobile/community/posts/{id}/scrap                    스크랩 토글 (인증)

GET    /api/mobile/community/posts/{id}/comments?cursor=&size=   댓글
POST   /api/mobile/community/posts/{id}/comments                 작성 (인증)
DELETE /api/mobile/community/comments/{id}                       삭제 (인증)
POST   /api/mobile/community/comments/{id}/like                  추천 토글 (인증)

POST   /api/mobile/community/polls/{id}/vote                     투표 (인증)
POST   /api/mobile/community/reports                             신고 (인증)
POST   /api/mobile/community/blocks                              차단 (인증)
DELETE /api/mobile/community/blocks/{memberId}                   차단 해제 (인증)
GET    /api/mobile/me/community/scraps                           내 스크랩
GET    /api/mobile/community/rules                               규칙 전문 (D-6)

POST   /api/auth/me/community-image/signature                    사진 업로드 서명 (인증)
```

**목록·상세는 선택적 인증이다.** 비회원도 읽어야 하고, 로그인했으면 내
좋아요·스크랩 여부와 차단 필터가 붙는다. `rating_repository` 의
`_optionalAuthGet` 과 같은 패턴이 앱 쪽에 이미 있다.

**쓰기 API 는 전부 서버에서 권한을 다시 검사한다.** 앱이 버튼을 가리는 건
UX 고, 전체 게시판/내 응원팀 게시판 판정은 서버가 최종이다. 앱만 믿으면
API 직접 호출로 남의 팀 게시판에 쓸 수 있다.

---

## 5-2. 참고 — 에브리타임은 어떻게 하나

같은 문제를 훨씬 큰 규모에서 푸는 곳이라 비교해 둘 값어치가 있다.


|        | 에브리타임                      | Warding                |
| ------ | -------------------------- | ---------------------- |
| 규모     | 대학생 대부분                    | 회원 6,497               |
| 신고 처리  | **신고 누적 자동 블라인드** (기준 비공개) | 3건에 Discord 알림, 판단은 사람 |
| 사전 필터링 | **AI 기반** (규칙 문서에 명시)      | 없음                     |
| 금칙어    | 운영자 설정 단어 즉시 차단            | 없음                     |
| 누적 제재  | 위반 횟수에 따라 제한 상향            | 같은 구조 (1회/2회/3회)       |


**에타가 자동화하는 건 좋아서가 아니라 사람이 감당할 수 없어서다.** 그 대가로
조직적 신고 악용을 떠안았고 — 정보성 글이 신고 몇 번에 내려가는 사례가 반복돼
2023년에 "이용제한 목록"(왜 제한됐는지 보여주는 화면)을 뒤늦게 추가했다.

우리는 신고량이 사람이 볼 수 있는 수준이라 **자동화의 이득 없이 부작용만
가져올 상황**이다. 회원 6.5천에서 3건은 마음먹으면 쉽게 모인다. D-3 을 알림으로
잡은 근거가 이것이다.

### 그래도 가져올 만한 것 둘

**1. 금칙어 즉시 차단** — 규모와 무관하게 값어치가 있다. AI 없이 단어 목록만으로
된다. 선수 실명 + 심한 욕설 조합 같은 건 신고를 기다릴 이유가 없다.

지금 설계에는 없다. 넣는다면 이 정도면 충분하다.

```sql
CREATE TABLE community_banned_word (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    word        VARCHAR(100) NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_community_banned_word UNIQUE (word)
);
```

작성 시점에 본문·제목을 훑어 걸리면 저장을 막고 이유를 돌려준다. 목록이 수백
개를 넘지 않으면 애플리케이션 메모리에 올려두고 검사해도 된다.

**2. "왜 제한됐는지" 보여주기** — 에타가 뒤늦게 넣은 기능이다. 제재를 시작할
때(D-1 쿨다운, `member_community_restriction`) **처음부터 이유를 보여주는 쪽이
낫다.** 나중에 붙이면 이미 불만이 쌓인 뒤다.

`member_community_restriction.reason` 을 이미 두었으니, 쓰기가 막힌 응답에
그 값을 실어 보내면 된다.

### 규모가 커지면

신고가 손으로 감당 안 되는 시점이 오면 결국 에타 경로를 밟는다 — 백오피스 큐 →
금칙어 → 임계 자동 블라인드. **그때는 임계를 공개하거나 최소한 이의제기 경로를
같이 만드는 것이 에타의 실수를 반복하지 않는 길이다.**

---

## 6. 작업 순서 (2026-08-26 검토 반영 확정)

백오피스 큐를 뒤로 미루기로 했으므로(D-7) 테이블·API 부터 간다.

1. **테이블 마이그레이션 (V79)** — 커뮤니티 테이블 전부 + 투표·제재 테이블 +
   `member.favorite_team_changed_at`(D-1). 전부 CREATE·INSTANT 컬럼 추가라
   후방호환이고, 미리 있어도 무해해서 한 방에 간다. **DDL 원문은 V79 가 단일
   진실이다** — 이 문서 1장의 SQL 은 초안이고, 검토에서 바뀐 점은 7장에 있다.
2. **목록·상세·작성 API** + 서버측 권한 재검사 (전체/내 응원팀 판정은 서버가 최종)
   + **D-1 쿨다운 검사** + **작성 간격 제한(D-9)**. 쿨다운을 7번에 뒀다가
   당겼다 — 도배 방지가 목적인데 출시 시점에 없으면 의미가 없고, 검사 조건
   한 줄이라 비용이 없다.
3. **신고·차단 API + Discord 알림** — 텍스트 3건, **이미지 1건**
4. **사진 업로드** — Cloudinary 서명 API. 프로필 사진과 같은 경로
5. 앱 연동 → `community_dummy.dart` 삭제
6. 투표
7. 제재 (`member_community_restriction` 발급·검사)
8. 백오피스 신고 큐 — 신고량이 손으로 감당 안 되기 시작하면
9. 금칙어 차단(5-2 참고) — 규모와 무관하게 값어치가 있어 더 앞으로 당겨도 된다

**3번을 4번보다 먼저 하는 게 중요하다.** 사진을 올릴 수 있는데 신고할 수 없는
구간이 생기면 안 된다.

---

## 7. 검토 반영 — 2026-08-26 확정

착수 전 재검토(락·인덱스·트레이드오프)에서 나온 결정들. V79 마이그레이션에 반영됐다.

### D-8. 회원 하드 삭제 × 커뮤니티 콘텐츠 — **확정: 콘텐츠는 SET NULL**

백오피스 `MemberDeleteService` 가 member 를 **하드 삭제**한다. 초안대로
글·댓글에 `ON DELETE CASCADE` 를 걸면 회원 삭제 한 방에 그 사람 글에 달린
**남의 댓글까지 전멸**하고(소프트 삭제를 둔 이유와 모순), 헤비 포스터 삭제가
한 트랜잭션에서 수천 행 X 락을 쥔다.

- **글·댓글(콘텐츠)**: `member_id NULL` + `ON DELETE SET NULL`. 화면은 "탈퇴한 사용자"
- **좋아요·투표·스크랩·차단·신고(행위 기록)**: CASCADE 유지 — 지워져도 맥락 파괴 없음
- 행위 기록 CASCADE 로 카운터가 그만큼 안 줄어드는 드리프트는 감수한다.
  회원 삭제는 드물다. 거슬리면 재집계 배치를 그때 붙인다

### D-9. 작성 간격 제한 — **확정: 글 60초 / 댓글 10초**

윈도우 카운트("시간당 N개")가 아니라 **간격 제한**이다 — 최근 1행만 보면 되고
(`idx_community_post_member (member_id, id DESC)` 최신 1행), 정상 유저는 안 걸린다.

- **status 무관하게 센다** — 지웠다 다시 올리는 우회를 막고, 필터 없는 쪽이 쿼리도 싸다
- 거부 응답에 `retryAfterSeconds` — D-1 쿨다운 응답과 같은 모양으로, 앱이 문구 하나로 처리
- 상수는 `application.yml` 프로퍼티 — 도배범 등장 시 숫자만 조인다
- 일일 상한은 안 넣는다. 간격 제한을 뚫는 도배범이 나타나면 같은 인덱스로 한 줄 추가

### 이름 충돌 — 레거시 `community_post` (배포에서 발견)

prod 에 `community_post` 가 이미 있었다 — 외부 커뮤니티(인벤/네이버/OPGG) **크롤링**
게시글 테이블(`app/community/`, v3 홈 Top5 위젯). 첫 배포가 이 충돌로 CrashLoop 났다
(RollingUpdate 라 서비스 무중단, V79 는 한 문장도 적용 전에 실패해 스키마 오염 없음).

**레거시를 `crawled_community_post` 로 개명하고(데이터 보존) 새 커뮤니티가 이름을
갖는다.** 앞으로의 진짜 커뮤니티가 제 이름을 쓰는 게 맞다. 덤으로 발견: 이 크롤러는
10분마다 돌지만 2026-07-31 이후 데이터가 없다 — 파서가 조용히 깨진 좀비. 정리(수리 or
기능 제거)는 별도 이슈로.

### 스키마 — 초안에서 바뀐 것 (V79 기준)

- **`community_post.edited_at` 추가** — "수정됨" 판정 전용. `updated_at` 은
  `ON UPDATE` 자동이라 블라인드 같은 status 변경에도 튀어서 오탐을 낸다
- **`idx_community_report_target` 제거** — `uk_community_report_once` 의
  `(target_type, target_id)` prefix 가 같은 조회를 커버한다. 대상당 신고는
  많아야 수십 행이라 status 잔여 필터는 공짜
- **좋아요 테이블의 member 선행 인덱스 제거** — "이 20개 글 중 내가 누른 것"은
  uk 점조회 20번으로 끝나고, "내가 좋아요한 목록" 화면은 없다
- **`community_scrap` 인덱스는 `(member_id, id DESC, post_id)` 커버링** —
  "내 스크랩" 페이지의 post_id 추출이 인덱스만으로 끝난다
- **`community_poll_vote.poll_id` 에 FK 안 건다** — 삭제 연쇄는 option FK 로
  이미 흐르고, 옵션-투표 소속 검증은 서버가 한다. insert 부모 락 하나를 던다

### 락 수칙 — 구현 시 지킬 것

- **카운터 갱신 트랜잭션 안에 외부 호출 금지.** 특히 신고 흐름의 Discord 웹훅은
  **커밋 후 발송**(`@TransactionalEventListener(AFTER_COMMIT)` 또는 커밋 뒤 async).
  트랜잭션 안에서 HTTP 를 쏘면 post 행 락을 수 초 쥔다
- **갱신 순서 통일**: 항상 자식 insert/delete → 부모 카운터 UPDATE.
  투표는 vote insert → option `vote_count` → poll `total_votes` 순서 고정.
  순서가 섞이면 데드락 경로가 생긴다
- 좋아요 더블탭은 uk 의 duplicate key 를 삼켜 멱등 처리. 데드락이 잡히면 재시도 1회
- `community_post` 행이 카운터 허브(댓글·좋아요·조회가 전부 같은 행 UPDATE)다.
  `view_count` 는 초안대로 트랜잭션 밖 단발 UPDATE
- 참고: 글·댓글 insert 는 FK 검사로 `member`·`teams` 부모 행에 S 락을 잡는다.
  새벽 4:15 팀 메타 sync(X 락)와 찰나 겹칠 수 있으나 무시 가능한 수준

### 쿼리 수칙

- **`board_team_id <=> :param` 을 쓰지 말고 쿼리를 분기한다** — `IS NULL` 버전과
  `= ?` 버전. NULL 파라미터 섞인 `<=>` 는 옵티마이저가 range 를 못 잡는 경우가 있다
- `(:blockedCount = 0 OR ...)` 트릭도 같은 이유로 — 차단 목록 유무로 쿼리를
  동적으로 조립한다(`*RepositoryImpl` 패턴)

### 소결정 (권장안대로 확정)

- 댓글 소프트 삭제 시 `comment_count` **-1 한다**(같은 트랜잭션) — 안 하면 목록 숫자 ≠ 상세 개수
- 답글 달린 댓글 삭제는 **"삭제된 댓글입니다" 자리 남김** — D-5 차단 표시와 같은 처리
- 투표는 1차에서 **변경·취소 불가** — 변경 허용은 옛 옵션 -1/새 옵션 +1 이 필요해지는데, 앱 UX 확정 후에
- 서버측 입력 상한: 이미지 글당 5장, 투표 옵션 2~6개, 본문 길이 — 앱 제한과 별개로 서버가 검증
- 차단 상대의 글 **상세 직진입(딥링크·스크랩 경유)도 차단 검사** — "차단한 사용자의 글" 응답
- 신고 insert 전에 **대상 실존·VISIBLE 검증** — 다형 참조라 FK 가 없으므로 서버가 막는다
- 답글 작성은 앱이 **`replyToCommentId` 만** 보낸다 — parent 올려붙이기(1단 고정)와
  `mention_member_id` 는 서버가 대상 댓글에서 유도한다. 앱이 직접 보내면 API 호출로
  관계없는 회원을 멘션에 꽂을 수 있고, 깊이 규칙이 앱·서버 두 군데 살게 된다
- 팀 게시판은 **댓글도 응원팀·쿨다운 검사를 탄다** — "쓰기"는 글·댓글 공통 규칙(D-1)

---

## 정리

**확정**

- **D-1** 응원팀 변경 후 팀 게시판 쓰기 **30일** 쿨다운
- **D-3** 신고 **3건**에 Discord 알림 (자동 블라인드 없음, 판단은 사람이)
- **D-7** **사진 1차 포함**, 백오피스 큐는 차후. 대응은 Discord 알림 + 수동 처리
  - **이미지 신고는 임계 없이 1건에 즉시 알림** (텍스트 3건과 다르다)
- **D-8** 회원 하드 삭제 시 글·댓글은 **SET NULL**("탈퇴한 사용자"), 행위 기록은 CASCADE (7장)
- **D-9** 작성 간격 제한 — **글 60초 / 댓글 10초**, status 무관, `retryAfterSeconds` 응답 (7장)

나머지(D-2 수정 허용, D-4 조회수 중복, D-5 차단 댓글 표시, D-6 규칙 서버화)는
추천안대로 간다. 스키마·락·쿼리 수칙의 검토 반영분은 7장.

**착수 전에 준비할 것**

- 커뮤니티 신고 전용 Discord 웹훅 채널 (`notification.discord.community-webhook-url`)
- 그 채널을 보는 사람과, 이미지 신고를 받았을 때 실행할 SQL

