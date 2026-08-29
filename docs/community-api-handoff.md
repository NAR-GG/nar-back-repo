# 커뮤니티 API 연동 가이드 — 앱 담당자용

백엔드 1~4단계가 prod(`https://api.nar.kr`)에 배포돼 있다. 이 문서 하나로
`community_dummy.dart` 를 실제 API 로 교체할 수 있게 쓴다.
설계 배경·결정 근거는 `docs/community-backend-design.md`.

- **총 20개 엔드포인트** (게시글 8, 댓글 4, 신고·차단 3, 내 활동 4, 사진 서명 1)
- 아직 없는 것: 투표, 규칙 전문 API(D-6), 제재 사유 응답, 금칙어 — 뒤 단계에서 온다
- Swagger: `https://api.nar.kr/swagger-ui.html` (Basic Auth 는 기존과 동일)

## 공통 규약

### 인증

기존 모바일 API 와 동일한 Bearer JWT.

- **읽기(GET, 조회수 +1)는 비로그인 허용** — 토큰 없이도 응답이 온다.
  토큰을 실으면 차단 필터·내 좋아요/스크랩 여부가 추가로 붙는다.
  `rating_repository` 의 `_optionalAuthGet` 패턴 그대로 쓰면 된다.
- **쓰기(POST/PUT/DELETE)는 전부 401** — 비로그인이면 시큐리티 레벨에서 막힌다.

### 에러 응답

기존과 같은 형태다.

```json
{ "timestamp": "...", "status": 403, "error": "FORBIDDEN",
  "code": "COMMUNITY_TEAM_COOLDOWN", "message": "응원팀을 바꾼 지 얼마 되지 않았습니다." }
```

**시간 조건 거부 2종은 `Retry-After` 응답 헤더(초)가 같이 온다.** 앱은 이 값으로
"N일/N초 뒤부터 쓸 수 있어요" 문구를 그린다.

| code | HTTP | 언제 | Retry-After |
|---|---|---|---|
| `COMMUNITY_TEAM_COOLDOWN` | 403 | 응원팀 변경 후 30일 안에 팀 게시판 쓰기 | O (남은 초) |
| `COMMUNITY_WRITE_INTERVAL` | 429 | 글 60초 / 댓글 10초 간격 위반 | O (남은 초) |
| `COMMUNITY_BOARD_FORBIDDEN` | 403 | 내 응원팀이 아닌 팀 게시판에 쓰기 | — |
| `COMMUNITY_NOT_AUTHOR` | 403 | 남의 글·댓글 수정/삭제 | — |
| `COMMUNITY_POST_NOT_FOUND` / `COMMUNITY_COMMENT_NOT_FOUND` | 404 | 삭제·블라인드 포함 | — |
| `COMMUNITY_ALREADY_REPORTED` | 409 | 같은 대상 중복 신고 | — |
| `COMMUNITY_BLOCK_SELF` | 400 | 자기 차단 | — |
| `INVALID_INPUT_VALUE` | 400 | 길이 초과, 잘못된 enum, 외부 이미지 URL 등 | — |

### 커서 페이지네이션

offset 없다. 전부 커서다.

- **글 목록·스크랩: 최신순(내림차순).** 응답의 `nextCursor` 를 다음 요청 `cursor` 로.
  `nextCursor == null` 이면 끝.
- **댓글: 오래된 순(오름차순).** 같은 방식.

## 게시글 (8)

### `GET /api/mobile/community/posts?boardTeamId=&cursor=&size=` — 목록

- `boardTeamId` 생략 = **전체 게시판**, 값 = 그 팀 게시판. `size` 기본 20, 최대 50
- 로그인 상태면 **차단한 사용자의 글이 서버에서 걸러져** 내려온다 (앱은 필터링 불필요)

```json
{ "posts": [ {
    "id": 42, "boardTeamId": null, "title": "...", "bodyPreview": "본문 앞 150자",
    "author": { "memberId": 7, "nickname": "이름#0001", "profileImageUrl": "...",
                "teamId": 1, "teamCode": "T1", "teamImageUrl": "..." },
    "viewCount": 10, "likeCount": 3, "commentCount": 5,
    "edited": false, "createdAt": "2026-08-26T21:00:00",
    "thumbnailUrl": "https://res.cloudinary.com/...", "imageCount": 2
} ], "nextCursor": 42 }
```

- **`author == null` 이면 "탈퇴한 사용자"로 그린다** (회원 하드 삭제 시 글은 남는다)
- `author.teamXxx` 는 **작성 시점** 응원팀 스냅샷 — 유저가 팀을 옮겨도 과거 글 뱃지는 안 바뀐다
- `edited` 가 "수정됨" 표시 기준. `updatedAt` 비교하지 말 것

**잠금 바 — `boardViewer`.** 팀 게시판 + 로그인일 때만 응답에 실린다(그 외 `null`).
앱은 쓰기 시도(403) 전에 이 값으로 잠금 바를 그린다:

```json
"boardViewer": { "canWrite": false, "reason": "COOLDOWN", "writableFrom": "2026-09-20T21:00:00" }
```

| reason | 잠금 바 문구 |
|---|---|
| `NOT_FAN` | "OO 팬만 글을 쓸 수 있어요" (기존 문구 — 이제 서버 판정으로 그린다) |
| `COOLDOWN` | "응원팀을 바꾼 지 얼마 되지 않았어요. N일 뒤부터 쓸 수 있어요" (N = writableFrom − 현재, 올림) |
| `null` (canWrite=true) | 잠금 바 없음, 쓰기 버튼 노출 |

서버 검사(403 + `Retry-After`)는 그대로 최종 방어선이다 — 잠금 바는 UX 일 뿐.

### `GET /api/mobile/community/posts/{id}` — 상세

목록 필드 + `body` 전문 + `images` + `viewer`:

```json
{ ..., "body": "전문",
  "images": [ { "id": 3, "url": "https://res.cloudinary.com/..." } ],
  "viewer": { "liked": true, "scrapped": false, "mine": false, "blockedAuthor": false } }
```

- `images[].id` 를 들고 있어야 한다 — **이미지 신고의 targetId** 다
- **`viewer.blockedAuthor == true` 면 `title`/`body`/`images` 가 비어서 온다** —
  "차단한 사용자의 글입니다" 자리를 그린다 (딥링크·스크랩 직진입 케이스)
- 비로그인이면 `viewer` 는 전부 false

### `POST /api/mobile/community/posts` — 작성

```json
{ "boardTeamId": null, "title": "≤100자", "body": "≤10,000자",
  "imageUrls": ["https://res.cloudinary.com/..."] }
```

- `boardTeamId: null` = 전체 게시판. 팀 게시판은 **내 응원팀만** 가능(서버가 재검사)
- `imageUrls` 최대 5장, **우리 Cloudinary secure_url 만** 통과 (아래 사진 플로우)
- 응답: `{ "id": 43 }`

### `PUT /api/mobile/community/posts/{id}` — 수정 (작성자만)

작성과 같은 몸체. **`imageUrls` 는 전체 교체** — `null` 보내면 이미지 변경 없음,
`[]` 보내면 전부 제거. 수정하면 `edited` 가 true 로 바뀐다.

### `DELETE /api/mobile/community/posts/{id}` — 삭제 (작성자만, 소프트)

### `POST /api/mobile/community/posts/{id}/view` — 조회수 +1

상세 진입 시 한 번 쏘고 응답은 버려도 된다(204). 비로그인 포함, 중복 허용(D-4 —
공지사항 `noticeViewUrl` 과 같은 방식).

### `POST /api/mobile/community/posts/{id}/like` — 추천 토글

응답 `{ "liked": true, "likeCount": 4 }` — **서버 카운트가 진실**이니 응답 값으로 갱신.
더블탭해도 안전(멱등).

### `POST /api/mobile/community/posts/{id}/scrap` — 스크랩 토글

응답 `{ "scrapped": true }`.

## 댓글 (4)

### `GET /api/mobile/community/posts/{postId}/comments?cursor=&size=` — 목록

오래된 순. `size` 기본 50, 최대 100. **1단 스레드는 앱이 조립한다** —
`parentId == null` 이 최상위, 값이 있으면 그 밑에 붙인다(항상 최상위를 가리킨다).

```json
{ "comments": [ {
    "id": 9, "parentId": 5, "body": "...", "status": "VISIBLE",
    "author": { ... }, "mentionNickname": "이름#0002",
    "likeCount": 1, "liked": false, "mine": false, "edited": false, "createdAt": "..."
} ], "nextCursor": 9 }
```

**`status` 4종 — VISIBLE 아니면 `body`·`author` 가 null 로 온다. 행은 유지된다(자리 보존):**

| status | 앱 표시 |
|---|---|
| `VISIBLE` | 정상 |
| `DELETED` | "삭제된 댓글입니다" |
| `BLOCKED` | "차단한 사용자의 댓글입니다" (D-5 — 답글 문맥 유지용) |
| `HIDDEN` | 운영 블라인드 — DELETED 와 같은 자리 문구로 무방 |

### `POST /api/mobile/community/posts/{postId}/comments` — 작성

```json
{ "body": "≤1,000자", "replyToCommentId": 5 }
```

- 최상위 댓글이면 `replyToCommentId` 생략
- **답글은 대상 댓글 id 만 보낸다.** 답글의 답글이어도 그 댓글 id 를 그대로 —
  parent 올려붙이기(1단 고정)와 멘션 대상은 서버가 유도한다. 앱이 parentId 를
  직접 계산하지 말 것
- 팀 게시판 글의 댓글도 응원팀·쿨다운 검사를 탄다(글과 같은 규칙)

### `PUT /api/mobile/community/comments/{id}` — 수정 (작성자만)

`{ "body": "≤1,000자" }` — 본문만 바뀐다(멘션·답글 관계 불변). 수정하면 `edited` 가
true. **글·댓글 모두 `edited == true` 면 "(수정됨)" 라벨을 붙인다** — 에타는 표시
안 하지만, 댓글 달린 뒤 본문 바꿔치기를 투명하게 만드는 장치라 우리는 표시한다.

### `DELETE /api/mobile/community/comments/{id}` — 삭제 (작성자만, 소프트)

### `POST /api/mobile/community/comments/{id}/like` — 추천 토글 (글과 동일)

## 신고·차단 (3)

### `POST /api/mobile/community/reports`

```json
{ "targetType": "POST", "targetId": 42, "reason": "SPAM", "detail": null }
```

- `targetType`: `POST` / `COMMENT` / `IMAGE` (이미지는 상세의 `images[].id`)
- `reason`: `ABUSE` / `OBSCENE` / `AD` / `FRAUD` / `SPAM` / `ETC` — **ETC 는 `detail`(≤200자) 필수**
- 같은 대상 재신고 → 409 (`COMMUNITY_ALREADY_REPORTED`) — "이미 신고했습니다" 처리
- 성공 204. 신고 후 서버 동작(앱은 몰라도 되지만 정책 공유): 텍스트 3건 누적 /
  이미지 1건 즉시 Discord 운영 알림 → 사람이 판단. **자동 블라인드 없음**

### `POST /api/mobile/community/blocks` — 차단

`{ "memberId": 7 }` → 204. 멱등(이미 차단이어도 204). 자기 자신은 400.
차단 즉시 **목록에서 그 사람 글이 사라지고, 댓글은 BLOCKED 자리로** 바뀐다.

### `DELETE /api/mobile/community/blocks/{memberId}` — 차단 해제 (멱등)

## 내 활동 (4) — 전부 인증 필수

**공통 정책: 삭제·블라인드된 것은 목록에서 숨긴다** (내가 쓴 글·댓글 포함).

### `GET /api/mobile/me/community/scraps?cursor=&size=`

```json
{ "items": [ { "scrapId": 12, "post": { ...PostSummary와 동일... } } ], "nextCursor": 12 }
```

커서는 `scrapId` 기준.

### `GET /api/mobile/me/community/posts?cursor=&size=` — 내가 쓴 글

응답은 게시글 목록과 동일(`{ posts, nextCursor }`, 커서 = 글 id). 전체·팀 게시판 글이 섞여
내려온다(`boardTeamId` 로 구분).

### `GET /api/mobile/me/community/likes?cursor=&size=` — 좋아요한 글

```json
{ "items": [ { "likeId": 7, "post": { ...PostSummary... } } ], "nextCursor": 7 }
```

커서는 `likeId` 기준(스크랩과 같은 모양).

### `GET /api/mobile/me/community/comments?cursor=&size=` — 내가 쓴 댓글

```json
{ "comments": [ { "id": 9, "postId": 3, "postTitle": "원글 제목", "body": "...",
    "likeCount": 1, "createdAt": "..." } ], "nextCursor": 9 }
```

최신순(커서 = 댓글 id). `postId` 로 원글 상세 이동. **원글이 삭제된 댓글도 목록에서
빠진다** — 눌러도 갈 곳이 없어서다.

## 사진 업로드 플로우 (1)

프로필 사진과 같은 서명 업로드다. **이미지 1장 = 서명 1회.**

1. `POST /api/auth/me/community-image/signature` (인증) →
   `{ cloudName, apiKey, timestamp, publicId, overwrite: false, signature, uploadUrl }`
2. 앱이 `uploadUrl` 로 직접 multipart 업로드 (프로필과 동일, `public_id`/`timestamp`/`signature`/`api_key` 필드)
3. 응답의 `secure_url` 을 모아 글 작성/수정 `imageUrls` 로 전송

- 프로필과 달리 **public_id 가 매번 새로 발급**된다(UUID) — 같은 서명 재사용 불가
- 서버는 `https://res.cloudinary.com/<우리 cloud>/` 로 시작하지 않는 URL 을 400 으로 거른다

## 정책 요약 — 앱 문구가 필요한 것

1. **팀 게시판 쓰기 쿨다운 30일 (D-1)** — 응원팀 변경 시. 403 + `Retry-After`(초).
   쓰기 시도가 거부되면 "응원팀을 바꾼 지 얼마 되지 않았어요. N일 뒤부터 쓸 수 있어요."
   (N = ceil(Retry-After/86400))
   - **프로필에서 응원팀을 바꾸려는 순간 사전 경고가 필요하다**: "변경하면 30일간 팀
     게시판에 글을 쓸 수 없어요" 확인 다이얼로그. 정적 문구라 API 불필요
2. **작성 간격** — 글 60초/댓글 10초. 429 + `Retry-After`. "잠시 후 다시 작성할 수 있어요."
3. **탈퇴한 사용자** — `author == null` 전부 공통 처리 (글·댓글·멘션)
4. **수정됨 표시** — 응답의 `edited` 불리언만 보면 된다
5. **입력 상한** — 제목 100자, 본문 1만 자, 댓글 1,000자, 사진 5장. 앱에서 선검증하면
   400 왕복이 줄지만, 최종 판정은 서버다

## 아직 서버에 없는 것 (더미 유지)

- **투표** — 테이블은 있고 API 는 6단계. 투표 UI 는 더미 유지 or 진입 숨김
- **규칙 전문 서버화 (D-6)** — `GET /api/mobile/community/rules` 예정. 그때까지 앱 내장 상수
- **제재(1회 경고/2회 7일/3회 30일)** — 7단계. 지금은 발생하지 않는다

## 같이 논의해서 정할 것 (미결)

1. ~~쿨다운 잠금 바를 넣을지 말지~~ — **해소.** 넣기로 확정. `/api/auth/me` 가 아니라
   **게시판 목록 응답의 `boardViewer`** 로 내려간다(화면과 데이터가 같이 오고, `/me`
   캐시 신선도 문제가 없다). 위 목록 절의 잠금 바 표 참고. NOT_FAN 판정까지 서버로
   통일됐으니 앱의 "내 응원팀 == 보드" 클라 판정은 걷어내도 된다.
2. ~~마이페이지에 "내가 쓴 글 / 내가 쓴 댓글" 추가 여부~~ — **해소.** 내 활동 4종
   (스크랩·내 글·좋아요한 글·내 댓글) API 가 전부 나갔다. 위 "내 활동" 절 참고.
   인덱스 추가 없이 기존 인덱스(FK 자동 인덱스 포함)로 돌고, 삭제·블라인드는 숨긴다.

## 참고

- 궁금한 응답 형태는 Swagger 가 최신이다 — 이 문서와 다르면 Swagger 가 맞다
- 서버 스키마·설계 결정 전문: `docs/community-backend-design.md` (nar-back-repo)
