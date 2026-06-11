# 모바일 API 변경 명세 (2026-06-11)

모바일 팀 백엔드 요청사항 대응 결과. 요청 항목 1·2·5·6번 구현, 3번은 기존 API 안내, 4번은 미구현(별도 작업 필요).

> 식별자 용어 정리
>
> | 필드 | 타입 | 용도 |
> |------|------|------|
> | `matchId` | String | 매치(BO3/BO5 단위) 식별자. 일정/리스트 API 공통 |
> | `gameId` | String | 세트(게임) 단위 esports 식별자. **라이브·선수 평점 API에서 사용** |
> | `recordGameId` | Long | 세트의 내부 DB 식별자. **기록(record) API에서 사용.** 기록 미적재 시 `null` |

---

## 1. 경기 리스트 커서 페이지네이션 (신규)

무한 스크롤용. 기존처럼 날짜별로 반복 호출할 필요 없이 한 번에 페이지 단위로 조회한다.

```
GET /api/mobile/matches
```

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `league` | String | `LCK` | 리그 필터 |
| `teamId` | Long | - | 팀 필터 (filters API의 teamId) |
| `cursor` | String | - | 이전 응답의 `nextCursor`. 첫 페이지는 생략 |
| `size` | int | 20 | 페이지 크기 (1~50) |

- 정렬: 최신 경기 → 과거 방향 (`matchDate DESC`)
- 커서는 불투명(opaque) 토큰. 그대로 돌려보내기만 하면 되고, 동시각 경기·실시간 경기 추가에도 중복/누락 없음
- `hasNext: false`(= `nextCursor: null`)면 마지막 페이지

응답 예시:

```json
{
  "league": "LCK",
  "teamId": null,
  "matches": [
    {
      "matchId": "113990000000000001",
      "date": "2026-04-01",
      "scheduledTime": "18:00",
      "matchStatus": "completed",
      "matchTitle": "T1 vs GEN",
      "leagueName": "LCK",
      "blueTeam": { "teamName": "T1", "teamCode": "T1", "teamImageUrl": "...", "score": 2 },
      "redTeam": { "teamName": "Gen.g", "teamCode": "GEN", "teamImageUrl": "...", "score": 1 },
      "liveStreamUrl": null,
      "games": [
        { "gameOrder": 1, "gameId": "113990000000000011", "recordGameId": 1024 },
        { "gameOrder": 2, "gameId": "113990000000000012", "recordGameId": 1025 },
        { "gameOrder": 3, "gameId": "113990000000000013", "recordGameId": null }
      ]
    }
  ],
  "nextCursor": "MjAyNi0wNC0wMVQwOTowMDowMHwxMTM5OTA...",
  "hasNext": true
}
```

## 2. 게임(세트) 식별자 노출

### 2-1. 경기 카드에 `date` / `games[]` 필드 추가

신규 리스트 API(위)와 **기존 일별 조회** `GET /api/mobile/schedules?date=` 양쪽 모두 경기 카드(`matches[]`)에 다음 필드가 추가됐다. 기존 필드는 그대로이므로 하위 호환.

- `date`: 경기 일자 (KST, `yyyy-MM-dd`)
- `games[]`: 세트 목록 `{ gameOrder, gameId, recordGameId }`. 세트 미생성 매치는 빈 배열

### 2-2. 매치 → 세트 목록 단독 조회 (신규)

```
GET /api/mobile/matches/{matchId}/games
```

```json
{
  "matchId": "113990000000000001",
  "games": [
    { "gameOrder": 1, "gameId": "113990000000000011", "recordGameId": 1024 }
  ]
}
```

- 매치 없음 → 404

## 3. 경기 상세 탭 데이터 (기존 API 안내)

| 탭 | API | 식별자 |
|----|-----|--------|
| 챔피언 밴픽 / 선수 스탯 | `GET /api/games/{recordGameId}/record` | `recordGameId` (Long) |
| 라이브 이벤트 타임라인 | `GET /api/live/games/{gameId}` (최신 상태 + `objectTimeline`), `GET /api/live/games/{gameId}/minutes` (최근 60분 스냅샷) | `gameId` (String) |
| 선수 평점 | `GET /api/mobile/live/games/{gameId}/ratings` 외 | `gameId` (String) |

record 응답에는 팀별 밴 목록, 선수 10명 스탯(KDA/CS/골드/데미지/타이밍별), 세트 네비게이션, 피어리스 드래프트가 포함된다.

## 5. 내 리뷰/평점 전체 목록 (신규)

```
GET /api/mobile/me/ratings?page=0&size=20
```

- **Bearer 인증 필수** (미로그인 401)
- 작성 최신순, page/size 페이지네이션 (size 최대 100)

```json
{
  "ratings": [
    {
      "ratingId": 11,
      "gameId": "113990000000000011",
      "participantId": 1,
      "playerId": 10,
      "playerName": "Faker",
      "playerImageUrl": "...",
      "teamSide": "Red",
      "role": "mid",
      "championName": "Ahri",
      "rating": 5,
      "comment": "역시 페이커",
      "createdAt": "2026-06-06T13:00:00",
      "updatedAt": "2026-06-06T13:00:00",
      "match": {
        "matchId": "113990000000000001",
        "gameOrder": 2,
        "leagueName": "LCK",
        "matchTitle": "DNS vs T1",
        "blueTeamCode": "DNS",
        "redTeamCode": "T1",
        "matchDate": "2026-06-06T18:00:00"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

- `match`는 세트-매치 매핑이 없으면 `null` (방어 처리 필요)
- `match.matchDate`는 KST

## 6. /auth/me 이메일 필드

`GET /api/auth/me` 응답(`MemberResponse`)에 `email` 추가.

```json
{
  "id": 12,
  "nickname": "용맹한바론",
  "email": "user@example.com",
  "favoriteLeagueName": "LCK",
  "favoriteTeamId": 3,
  "favoritePlayerIds": [10, 11],
  "isOnboarded": true
}
```

- **소셜 로그인 시 이메일 제공에 동의하지 않은 회원은 `null`** — UI에서 null 처리 필요

---

## 남은 작업 (이번 배포 미포함)

| 항목 | 상태 | 비고 |
|------|------|------|
| 4. 구글·네이버 모바일 로그인 (`POST /auth/mobile/google`, `/naver`) | **미구현** | 카카오(`POST /api/auth/mobile/kakao`)만 운영 중. 카카오 패턴 따라 신규 구현 필요 |
| 경기 리스트 시즌(스플릿) 필터 | 미지원 | `LeagueMatch`에 시즌 컬럼이 없음. 당장은 날짜 범위로 대체, 필요 시 컬럼 추가 검토 |
| record API의 String gameId 직접 지원 | 검토 | 현재는 `games[].recordGameId`로 변환값을 내려주는 방식으로 해소 |
