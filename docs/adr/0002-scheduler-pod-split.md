# 스케줄러를 별도 파드로 뗀다 — 그 대가는 인메모리 상태의 분할이다

웹 파드 하나가 API 와 `@Scheduled` 26개를 같이 들고 있었다. 두 가지가 막혀 있었다:
**웹을 늘릴 수 없었고**(replicas 2 → 라이브 폴링·푸시가 두 벌), **웹을 배포할 때마다
스케줄러가 같이 재시작해 라이브 폴링에 구멍이 났다**. 그래서 같은 이미지에
`APP_SCHEDULING_ENABLED` 만 갈라 Deployment 를 하나 더 뒀다 (2026-08-22 12:49, #442).

`nar-scheduler` 는 `replicas: 1` + `strategy: Recreate` 다. 중복 실행 가드(ShedLock 등)가
없어서 두 벌이 돌면 알림이 두 번 나가고, 그건 되돌릴 수 없다. RollingUpdate 면
`maxSurge` 올림으로 배포마다 40~60초 겹친다.

## 대가 — 이게 이 ADR 의 본론이다

**파드가 둘이 되면 JVM 이 둘이고, 인메모리 상태도 둘이다.** 캐시(Caffeine)는 #442 에서
TTL 로 처리했다. **처리하지 않은 것은 라이브 상태다.**

`LiveStateStore` 는 순수 인메모리다(`ConcurrentHashMap`, 리포지토리 없음). 이걸 채우는
주체는 `LiveFrameProcessor` 이고, 그건 `@Scheduled` 폴링 경로에서만 불린다. 즉:

- **`nar-scheduler`**: `activeGames`·`latestStates`·`finishedGameIds` 가 채워진다
- **`nar-web`**: 같은 빈이 있지만 **영구히 빈 상태다.** 그런데 사용자 트래픽은 전부 여기로 온다
  (`traefik-routes.yaml` 의 라우터 3개가 모두 `nar-web`)

읽는 쪽이 셋으로 갈린다.

| 경로 | 폴백 | 결과 |
|---|---|---|
| `LiveStateQueryService.getLatestState()` | 인메모리 → 없으면 `live_game_minute_snapshot` | **동작한다.** 분 버킷 행이 폴마다(5초) UPSERT 되므로 지연은 5~10초 |
| `LiveActivityCatchUpService.liveGameOf()` | 없음 | **정지했다.** 아래 실측 참고 |
| `MobileScheduleService` 의 세트 LIVE 판정 | 없음 (`recordedGameIds` 가 일부만 보완) | 진행 중 세트가 `ENDED` 로 내려갈 것으로 보인다. **미검증** |
| `LiveController` `GET /api/live/games` | 없음 | 항상 빈 배열. 운영·진단용이라 사용자 영향 없음 |

### 실측 (Loki, 2026-08-23)

```
{namespace="nar", pod=~"nar-web-.*"} |= "live-activity"
  분리 전  08-21 22:39 · 22:46  →  '구독 직후 카드 생성' 있음
  분리 후  08-22 12:49 ~        →  0 건 (429,648 줄 스캔)
같은 구간 nar-scheduler 는 정상 (set-start 4건, [live-activity] 4건)
```

`LiveActivityCatchUpService` 는 세트 **진행 중에** 구독한 사람에게 잠금화면 카드를
띄우는 폴백이었다. 분리 이후 웹의 store 가 비어 무동작이다.

**그래도 사용자 영향이 거의 없는 이유**: 카드를 만드는 주체가 둘이고 주 경로는 앱이다.
앱이 ActivityKit 으로 직접 만들고(`ios/Runner/LiveActivityPlugin.swift`) 토큰을
`POST /api/mobile/me/live-activities` 로 올린다. 서버 push-to-start 는 앱이 실행 중이
아닐 때의 경로다. 그래서 이 정지는 "앱이 카드를 못 만드는 상황에서 구독이 일어날 때"만
드러난다.

## 그래서 무엇을 하지 않았나

카치업을 고치지 않았다. 두 갈래인데 어느 쪽인지 아직 모른다 —
**앱이 항상 카드를 만들 수 있으면 이 서비스는 죽은 코드라 지우는 게 맞고**,
그렇지 않으면 `liveStateStore` 대신 `live_game_minute_snapshot` 을 보게 바꾸는
한 줄짜리 수정이다. 앱 쪽(`warding-mobile-repo`)에서 구독 시 카드 생성이 실패하거나
생략되는 경로가 있는지 확인한 뒤 정한다.

## 다음에 파드를 또 쪼갤 때

**인메모리 상태를 먼저 세어라.** #442 는 캐시를 셌고 라이브 상태를 놓쳤다.
검색법: 필드가 `ConcurrentHashMap`·`newKeySet` 인 스프링 빈을 찾고, 그 빈을 읽는
코드가 웹 요청 경로에 있는지 본다. 있으면 DB 폴백이 있는지 확인한다.
