# 알림 세분화 설계 — 라이브 이벤트 5종 분리 + 솔랭 종료 알림

작성일: 2026-08-16

## 배경

두 가지 요구가 들어왔다.

1. **경기 알림 세분화** — 특히 `LIVE_EVENT` 를 쪼개 달라
2. **솔랭 알림을 게임 종료 시점에도** 보내 달라

## 측정 데이터

측정 시점 2026-08-16, 출처는 프로덕션 DB(`nardb`)와 CloudWatch `/nar/app`.

### 라이브 이벤트가 푸시의 81%

최근 7일 `member_team_event_push_delivery`:

| 타입 | 발송 |
|---|---|
| LIVE_EVENT | 258,525 |
| SET_END | 31,388 |
| SET_START | 29,962 |

세트당 이벤트(`live_game_object_event`, 7일 / 120세트):

| 타입 | 세트당 | 비중 |
|---|---|---|
| KILL | 29.7 | 60% |
| TOWER | 11.7 | 24% |
| DRAGON | 4.3 | 9% |
| INHIBITOR | 1.8 | 4% |
| BARON | 1.4 | 3% |

세트당 49건. 킬과 타워가 84%를 차지한다.

### 구독 규모

| 구독 | 행 수 |
|---|---|
| `member_favorite_player` (선수/솔랭) | 24,431 |
| `member_team_notification_subscription` | 6,406 |
| `member_match_subscription` | 1,864 |

`live_event_enabled = true`: 경기 구독 1,619 + 팀 구독 1,852 = **3,471**.

### 솔랭 모니터

- 추적 계정 101개 (KR 94 / EUW1 4 / NA1 3)
- 폴 주기 60초, `max-requests-per-second: 10`
- 솔랭 게임 하루 257건, 피크(새벽 1시) 시간당 40건

### Riot 429 현황

| 날짜 | 429 |
|---|---|
| 08-10 | 12 |
| 08-11 | 657 |
| 08-12 | 699 |
| 08-13 | 91 |
| 08-14 | 0 |
| 08-15 | 31 |
| 08-16 | 0 |

7일간 429가 발생한 선수는 6명뿐이고 **전부 비-KR 계정**이다(Kyeahoo·Way·Paduck·Hype = EUW1, Loki·Quad = NA1). KR 94계정은 0건. `systemic` 경고도 7일간 0건.

즉 레이트리밋은 우리 볼륨 문제가 아니라 리전별 버킷 문제다. **KR 쪽에는 호출 여유가 있다.**

## A. 경기 알림 세분화

### 결정: 5종 개별 토글

`LIVE_EVENT` 를 KILL / BARON / DRAGON / TOWER / INHIBITOR 다섯 개로 나눈다. 기존 `live_event_enabled` 는 **마스터 스위치로 유지**하고 그 아래 5개를 둔다.

```
경기 알림
 ├ 세트 시작        [ON]
 ├ 세트 종료        [ON]
 └ 라이브 이벤트     [ON]   ← 마스터
    ├ 킬            [OFF]
    ├ 바론          [ON]
    ├ 드래곤        [ON]
    ├ 타워          [OFF]
    └ 억제기        [ON]
```

발송 조건은 `live_event_enabled AND <타입>_enabled`.

### 저장: 불리언 컬럼 5개씩

기존 3토글이 이미 `MemberDeviceRepository` 에서 `CASE` 패턴으로 처리되고 있어 그대로 확장된다.

비트마스크 1컬럼도 검토했으나 버렸다 — JPQL 에서 비트 연산에 벤더 함수가 필요하고, 백오피스에서 값을 읽을 수 없다. 별도 테이블도 버렸다 — 팬아웃 쿼리가 이미 무거운데 조인이 하나 더 붙는다.

이벤트 종류가 늘어날 때(아타칸·공허 유충 등) Flyway 한 줄이 더 필요하지만, 몇 년에 한 번이라 비트마스크의 상시 비용보다 싸다.

### 앱 호환성 — FCM `type` 은 바꾸지 않는다

앱이 FCM data 의 `type` 값으로 라우팅한다(`fcm_notification_types.dart:18`, `member_notification.dart:91`). `type` 을 `KILL` 등으로 바꾸면 배포된 구버전(1.0.15 이하)에서 알림 분류가 깨진다.

- FCM payload `type` = `LIVE_EVENT` 유지
- 세부 타입은 새 필드 `eventSubType` 으로 추가 (앱은 당분간 무시)
- `member_team_event_push_delivery.event_type` 도 `LIVE_EVENT` 유지 — 멱등키가 `event_order`(전역 이벤트 id)라 타입을 바꿀 이유가 없고, 바꾸면 배포 중 중복 발송이 난다

### 코드 흐름

지금 `eventType` 이 푸시 경로에서 버려진다. `LiveObjectEventRecorder.fireLiveEventPush` 가 title/body 만 넘긴다. 태워 보낸다.

```
LiveObjectEventRecorder.fireLiveEventPush(.., eventType, ..)      ← 인자 추가
  → TeamLiveEventPushService.notifyLiveEvent(.., eventType, ..)   ← 인자 추가
    → fanOut / fanOutToMatchSubscribers(.., eventType, ..)
      → MemberDeviceRepository.findActiveDevicesBySubscribedTeamId
        findActiveDevicesBySubscribedMatchId                       ← CASE 5줄 추가
```

호출 지점은 두 곳이다 — 오브젝트 이벤트(`LiveObjectEventRecorder:335`)와 킬 이벤트(`:401`).

### 백필: 기존 3,471명은 5개 모두 ON

현상 유지로 간다. 앱의 토글 UI 가 나중에 배포되므로, OFF 로 백필하면 사용자가 되돌릴 방법이 없는 상태에서 알림이 조용히 줄어든다. 세분화의 이득은 "끄고 싶은 사람이 끄는 것"에서 나오지 기본값을 바꾸는 데서 나오지 않는다.

신규 구독의 기본값은 기존 `live_event_enabled` 기본값 정책을 따른다(경기 구독 ON, 팀 구독 OFF).

### 피처 플래그 불필요

백필이 전부 ON 이므로 서버 동작이 100% 동일하다. 앱이 토글 UI 를 배포해야 비로소 값이 갈린다.

## B. 솔랭 종료 알림

### 두 경로를 모두 쓴다

두 경로는 대상이 겹치지 않는다.

| 경로 | 대상 | 상태 |
|---|---|---|
| **전이 기반** | 라이브 감지되는 계정 | 신규 |
| **match-v5 폴백** | 스트리머 모드 계정 | 코드 있음, prod OFF |

### B-1. 전이 기반 (라이브 감지 게임)

`PlayerSoloRankMonitorService` 가 60초마다 계정 상태를 갱신하고, `PlayerRiotAccount.liveStatus` 가 `IN_RANKED_SOLO → OFFLINE` 으로 바뀌는 순간이 곧 "그 게임 끝남"이다. `lastCheckedMatchId` 가 어느 게임인지 들고 있다(`PlayerSoloRankMonitorService:90-94`, `markNoRecentMatch`).

그 전이에서만 종료 체크를 걸고, match-v5 가 아직 없으면 재시도한다.

폴링(전 계정 1분 주기)과 비교:

| | 1분 폴링 | 전이 기반 |
|---|---|---|
| Riot 호출 | 101/분 = 6,060/시간 | 게임당 1~n회, 피크 40/시간 |
| 종료 감지 지연 | 최대 60초 | 최대 60초 |
| match-v5 발행 대기 | 최대 60초 재시도 | 30초 재시도 |

지연이 같거나 낫고 호출은 150분의 1이다. 폴링은 아무 일도 없는 94계정에게 매분 묻는 비용을 낸다.

### B-2. 폴백 활성화 (스트리머 모드)

`RIOT_MATCH_FALLBACK_ENABLED` 가 `deploy.yml` 에 없어 기본값 `false` 다. **`PlayerSoloRankMatchFallbackService` 는 프로덕션에서 한 번도 실행된 적이 없다.** 따라서 스트리머 모드 계정 구독자는 지금 시작 알림도 종료 알림도 받지 못한다.

`deploy.yml` 에 플래그를 **명시적으로 `false`** 로 추가하고(문서화 목적), 검증 후 켠다.

폴백은 이미 `notifySubscribersPostGame`("한 판 마쳤어요" + `buildResultLine`)을 부르므로 종료 알림 문구는 그대로 쓴다.

### 스트리머 모드 규모는 아직 모른다

14일간 솔랭 게임 0건인 추적 계정이 19/101 이다.

```
Aiming, Berserker, Beryl, Casting, Cuzz, Ellim, Enosh, Hambak, Hype(EUW1),
Jiwoo, Life, Morgan, Painter, Peanut, Perfect, Pleata, Soboro, Trigger, Zinie
```

이 중 스트리머 모드와 "그냥 안 하는 선수"가 구분되지 않는다. `RiotApiClient:160` 이 404 를 모두 `empty` 로 뭉개서 스트리머 모드의 `"filtered"` 404 와 "게임 중 아님" 404 가 같아진다.

**선행 작업**: 404 응답 본문의 `filtered` 여부를 구분해 로그에 남긴다. 며칠이면 실제 스트리머 모드 명단이 나오고, 그게 폴백을 켤 가치가 있는지 판단할 근거다. 코드 몇 줄이라 이번 범위에 포함한다.

### 사용자 토글

`MemberFavoritePlayer` 에는 지금 토글 컬럼이 없다(구독 여부만).

- `start_enabled` 기본 `true` — 기존 24,431 구독의 현재 동작
- `end_enabled` 기본 `false` — 켜고 싶은 사람만

### 중복 방지 수정

`player_solo_rank_push_delivery` 의 멱등키가 `(member_id, game_id)` 라 **시작을 보냈으면 종료가 막힌다**. 키에 타입을 추가한다.

### 재조회 방지

`player_solo_rank_game` 에 `end_notified_at` 을 둔다. 없으면 종료 처리가 끝난 게임을 매 주기 다시 조회해 Riot 쿼터가 샌다.

### 전역 피처 플래그

`solo-rank.end-notification.enabled` (env `SOLO_RANK_END_NOTIFICATION_ENABLED`), 기본 `false`.

사용자 토글(`end_enabled`)과 별개다. 전역 플래그가 꺼져 있으면 전이 감지·match-v5 조회·발송이 전부 skip 된다. 앱 UI 가 준비되기 전에는 아무에게도 나가지 않는다.

## 스키마 변경

Flyway 마이그레이션 1개.

```sql
-- 라이브 이벤트 5종 (기존 구독은 전부 ON = 현상 유지)
ALTER TABLE member_match_subscription
  ADD COLUMN kill_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN baron_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN dragon_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN tower_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN inhibitor_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE member_team_notification_subscription
  ADD COLUMN kill_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN baron_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN dragon_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN tower_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN inhibitor_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 솔랭 시작/종료 토글
ALTER TABLE member_favorite_player
  ADD COLUMN start_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN end_enabled   BOOLEAN NOT NULL DEFAULT FALSE;

-- 종료 처리 완료 표시 (Riot 재조회 방지)
ALTER TABLE player_solo_rank_game
  ADD COLUMN end_notified_at DATETIME NULL;

-- 시작/종료 멱등키 분리
ALTER TABLE player_solo_rank_push_delivery
  ADD COLUMN event_type VARCHAR(16) NOT NULL DEFAULT 'START';
ALTER TABLE player_solo_rank_push_delivery
  DROP INDEX uk_player_solo_rank_push_delivery,
  ADD UNIQUE KEY uk_player_solo_rank_push_delivery (member_id, player_id, game_id, event_type);
```

기존 유니크 키는 `uk_player_solo_rank_push_delivery (member_id, player_id, game_id)` 로 확인했다(3컬럼). `event_type` 을 뒤에 붙인다.

## API 변경

기존 구독 API 의 요청/응답 DTO 에 필드를 추가한다. 모두 optional 이며, 빠지면 기존 값을 유지한다(구버전 앱 호환).

| 엔드포인트 | 추가 |
|---|---|
| `POST /api/mobile/me/match-subscriptions` | 5개 이벤트 필드 |
| `PUT /api/mobile/me/notification-subscriptions/{teamId}` | 5개 이벤트 필드 |
| `POST /api/mobile/me/player-subscriptions` | `startEnabled`, `endEnabled` |
| `GET /api/mobile/me/player-subscriptions` | 응답에 두 값 노출 |

경기 구독은 지금 `POST`(생성) 와 `DELETE` 만 있고 부분 수정 경로가 없다. 앱이 토글만 바꿀 수 있어야 하므로 `PUT /api/mobile/me/match-subscriptions/{matchId}` 를 추가한다. 팀 구독은 이미 `update` 가 있어 필드만 늘리면 된다.

선수 구독도 `POST` 와 `DELETE` 뿐이라 `PUT /api/mobile/me/player-subscriptions/{playerId}` 를 추가한다.

## 롤아웃 순서

1. 스키마 + 서버 로직 머지 (`SOLO_RANK_END_NOTIFICATION_ENABLED=false`, `RIOT_MATCH_FALLBACK_ENABLED=false`)
2. 404 `filtered` 구분 로그로 스트리머 모드 명단 확보 (며칠)
3. 앱 토글 UI 배포
4. `SOLO_RANK_END_NOTIFICATION_ENABLED=true`
5. 폴백은 2번 결과를 보고 별도 판단

## 테스트

- `MemberDeviceRepository` 쿼리 — 타입별 토글 조합에 맞는 기기만 나오는지 (`@DataJpaTest`)
- `TeamLiveEventPushService` — `eventType` 이 쿼리까지 전달되는지, FCM payload `type` 이 `LIVE_EVENT` 로 유지되는지
- 전이 감지 — `IN_RANKED_SOLO → OFFLINE` 에서 종료 체크가 큐잉되고, 같은 게임이 두 번 처리되지 않는지(`end_notified_at`)
- 멱등 — 같은 게임에 START 와 END 가 각각 1회씩 나가는지
- 전역 플래그 OFF 시 Riot 호출과 발송이 전부 skip 되는지

## 측정하지 못한 것

- **Riot match-v5 발행 지연** — 종료 알림 지연의 실제 하한을 정하는 값인데, `player_solo_rank_game` 에 `gameEndTimestamp` 를 저장하지 않고 폴백이 꺼져 있어 관측 데이터가 없다. 전이 시각과 match-v5 최초 성공 시각을 로그로 남겨 며칠 뒤 재시도 간격(초안 30초)을 조정한다.
- **스트리머 모드 계정 수** — 위 404 구분 작업으로 확보한다.

## 리스크

- 비-KR 7계정(EUW1 4 / NA1 3)은 spectator 429 를 간헐적으로 맞는다. 8/11~12 에는 폴 사이클의 약 11%가 스킵됐다. 전이 감지도 같은 폴링에 의존하므로 이 계정들은 종료 감지가 늦을 수 있다. 지금은 잠잠하고(8/14·8/16 0건) 게임이 20~35분이라 한두 사이클 스킵은 회복된다.
- 폴백을 켜는 시점에 최근 게임이 한꺼번에 잡힐 수 있다. `alert-freshness-minutes: 60` 이 막지만 실측이 필요하다.
