# 솔랭 종료 알림 — 앱 작업 인수인계

작성 2026-08-19. 백엔드 작업은 끝났고 prod 에 반영됐다. 남은 건 앱 화면뿐이다.

## 한 줄 요약

서버는 솔랭 **종료** 알림을 발송·적재하고 있는데(오늘 27건 실측), 앱 마이구독 피드가 솔랭 알림만
서버 문구를 버리고 직접 조립하기 때문에 종료 알림이 **"솔랭을 시작했어요"로 보인다**. 승패·KDA 도
화면에 닿지 못한다. 서버 push data 에 `eventType`·`win`·`kda` 를 실어놨으니 앱에서 분기하면 된다.

## 현재 상태

| 구성 | 상태 |
|---|---|
| 서버 로직·스키마 (#394) | prod 반영 |
| 전역 플래그 `SOLO_RANK_END_NOTIFICATION_ENABLED` (#424) | **ON** (2026-08-19 14:16 부터) |
| 종료 push data 확장 (#425) | prod 반영 (이미지 `a6e75b9`) |
| 앱 선수 알림 토글 UI (mobile #198, 1.0.17+32) | 코드 머지됨 |
| 앱 종료 알림 표시 | **미구현 ← 이 문서의 작업** |

발송 실측 (prod `player_solo_rank_push_delivery`, 플래그 ON 이후):

```
END   SENT 27건   (마지막 16:27)
START SENT 38,918건
```

알림함 적재도 정상이다. prod `member_notification` 실제 행:

```
"Pyosik 선수가 솔랭 한 판을 마쳤어요" / "리 신으로 승리 · 18/1/11"
```

즉 **데이터는 다 와 있고 화면만 안 그린다**.

## 왜 시작 알림처럼 보이나

`lib/screens/subscription/subscription_screen.dart:222`

```dart
if (n.type == MemberNotificationType.playerSoloRank) {
  card = RankStartNotification(       // ← 종료 알림도 무조건 이 카드
    playerName: n.playerName,
    champion: n.championName,
    queueType: n.queueType,
    ...
  );
} else {
  card = NotificationCard(title: n.title, body: n.body, ...);   // 팀 이벤트는 서버 문구 사용
}
```

`lib/screens/subscription/component/rank_start_notification.dart:44`

```dart
title: l.rankStartTitle(playerName),
body: l.rankStartBody(playerName, resolvedChampion, particle, resolvedQueue),
```

팀 이벤트는 서버 `title`/`body` 를 그대로 쓰는데 솔랭만 예외로 클라이언트에서 재조립한다(로케일 대응
때문). 그래서 서버가 보낸 "한 판을 마쳤어요 / 리 신으로 승리 · 18/1/11" 이 버려진다.

## 서버가 주는 data 계약

`GET /api/mobile/me/notifications` 의 `data`, 그리고 FCM `data` 페이로드 둘 다 같다.

| 키 | 값 | 언제 |
|---|---|---|
| `type` | `PLAYER_SOLO_RANK_STARTED` | 항상 (시작·종료 동일) |
| `eventType` | `START` / `END` | 항상 |
| `win` | `"true"` / `"false"` | `END` + 결과를 읽었을 때만 |
| `kda` | `"18/1/11"` | `END` + K/D/A 셋 다 있을 때만 |
| `playerId`, `playerName`, `gameId`, `championName`, `queueType`, `deepLink` | 기존과 동일 | 항상 |
| `championImageUrl`, `opggUrl` | 기존과 동일 | 값이 있을 때 |

**`type` 은 안 바뀐다.** 앱 딥링크 라우팅 키라서 시작·종료가 같은 값을 쓴다. 구분은 `eventType` 으로만
한다. 서버 `title`/`body` 는 한국어 문구이고, `body` 가 곧 결과 문구다("리 신으로 승리 · 18/1/11").

### 실제 페이로드

종료 (결과 있음):

```json
{
  "type": "PLAYER_SOLO_RANK_STARTED",
  "eventType": "END",
  "win": "true",
  "kda": "18/1/11",
  "playerId": "92",
  "playerName": "Pyosik",
  "gameId": "8346018893",
  "championName": "리 신",
  "queueType": "솔로 랭크",
  "deepLink": "nar://players/92",
  "championImageUrl": "https://ddragon.../LeeSin.png",
  "opggUrl": "https://www.op.gg/summoners/kr/DNS+Pyosik-KR2"
}
```

종료 (match-v5 결과를 못 읽은 경우) — `win`·`kda` **키가 아예 없다**. `containsKey` 로 분기하면 된다.
이때 서버 `body` 는 "리 신 경기 종료" 형태다.

시작 — `eventType: "START"`, `win`·`kda` 없음. 나머지는 기존과 동일.

## 앱에서 할 일

1. **모델 접근자 추가** — `lib/model/member_notification.dart` 의 `_d()` 편의 접근자 옆에

   ```dart
   bool get isSoloRankEnd => _d('eventType') == 'END';
   bool? get soloRankWin => switch (_d('win')) { 'true' => true, 'false' => false, _ => null };
   String? get kda => _d('kda');
   ```

2. **카드 분기** — `subscription_screen.dart:222` 에서 `n.isSoloRankEnd` 면 종료 카드로.
   `RankStartNotification` 을 복사해 `RankEndNotification` 을 만들거나, 같은 위젯에 `isEnd` 플래그를
   받아도 된다(레이아웃은 동일해도 됨).

3. **l10n** — `rankEndTitle`/`rankEndBody` 를 ko·en 양쪽에 추가. `win`·`kda` 를 원자값으로 받는 이유가
   이거다. 문안 제안:

   - ko: `{player} 선수가 솔랭 한 판을 마쳤어요` / `{champion}으로 승리 · 18/1/11`
   - en: `{player} finished a solo queue game` / `Win with {champion} · 18/1/11`
   - `win` 없음: `{champion} 경기 종료` / `Game ended with {champion}`
   - `kda` 없음: 승패만 표기하고 ` · ` 뒤를 생략

4. **아이콘** — `_iconFor()` 는 지금 시작·종료 모두 `headset.svg` 다. 종료용 아이콘 분리 여부는 디자인
   확인 후 결정.

5. **탭 동작** — 그대로 두면 된다. 종료도 OP.GG 로 가는 게 맞고 `opggUrl` 이 실려 있다.

6. (선택) **필터 칩** — 지금 타입 칩은 세트 시작/종료/라이브뿐이고 솔랭은 선수 필터로만 걸린다.
   솔랭 시작/종료를 칩으로 나눌지는 UX 판단.

## 확인 부탁

- **1.0.17+32 가 실제로 배포됐는지.** 선수별 솔랭 시작/종료 토글 UI(#198)는 코드에 있고 태그
  `release/1.0.17+32` 도 있지만, 모바일 레포엔 빌드·배포 워크플로가 없어서(PR Discord 알림만 돎)
  스토어·TestFlight 반영 여부를 저장소에서 확인할 수 없다.
- **종료 알림은 기본 OFF 다.** `member_favorite_player.end_enabled` 기본값 `false` 로, 현재 켜둔
  구독이 16개뿐이다(시작은 26,157개). 테스트할 땐 마이구독 설정 → 구독 알림 세부 설정 → **선수 탭**
  에서 「솔랭 종료 알림」을 먼저 켜야 한다. 기본 탭이 팀이라 선수 탭을 안 누르면 토글이 안 보인다.

## 검증 방법

1. 선수 탭에서 종료 알림 ON (구독 선수 중 솔랭 자주 도는 선수 권장).
2. 그 선수가 한 판 마치면 푸시가 온다. 감지는 라이브 모니터 전이 기반이라 게임 종료 후 최대 5분
   (30초 스윕 × 10회) 안에 발송되고, match-v5 가 그때까지 안 나오면 발송을 포기한다.
3. 마이구독 피드에서 카드 문구 확인. 지금은 시작 알림과 동일하게 보이는 게 정상(=버그 재현).

## 알려진 제약 (앱 작업과 무관)

- 스트리머 모드 계정은 라이브 감지가 안 돼 전이가 없다. 그쪽은 match-v5 폴백이 담당하는데
  `RIOT_MATCH_FALLBACK_ENABLED` 가 아직 false 다(Riot 쿼터 판단 필요). 해당 선수는 종료 알림이 안 온다.
- 이미 끝난 게임은 소급 발송하지 않는다(대기열이 인메모리라 전이 시점에만 등록된다).

## 관련 PR

- 백엔드 #394 알림 세분화(라이브 이벤트 5종 + 솔랭 종료), #424 전역 플래그, #425 push data 확장
- 앱 #198 마이 구독 설정 선수/팀 알림 API 연동, #200 1.0.17+32
