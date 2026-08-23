# 라이브위젯(Live Activity) 장애 케이스 정리 — 2026-08-23

같은 날 접수된 신고 3건을 추적한 결과다. 증상은 셋인데 원인은 **4개의 구조적 결함**으로 수렴하고,
그중 하나는 어제 파드를 뗀 것(#442)의 부작용이다.

## 검증 기준

| 대상 | 확인한 것 |
|---|---|
| 백엔드 | 배포 이미지 `menten4859/nar-gg:2e17163b…` = `origin/main` `2e17163b` **완전 일치**. 인용한 코드는 배포 sha 에서 `git show` 로 재확인 |
| 모바일 | 최신 릴리스 `release/1.0.18+40`(2026-08-22 01:14) = `origin/main` `806cec9`, live-activity·auth 경로 **diff 0**. 인용 라인은 릴리스 태그에서 확인 |
| 이전 릴리스 | `release/1.0.17+39`(08-20)도 `readOrThrowIfUnavailable`·`first_unlock_this_device` 동일 — 두 버전 동작 차이 없음 |

실서비스 앱은 Shorebird 패치가 얹힐 수 있어 소스와 100% 동일하다는 보장은 없다.

## 요약

| # | 신고 | 증상 | 주 원인 | 규모 |
|---|---|---|---|---|
| A | 내부(member 10) | 1세트는 떴는데 2세트부터 없음 | 원인 3 → 유령 B토큰, 원인 0 으로 자력 복구도 막힘 | 카드가 중도 소실된 사람 |
| B | soy***@gmail.com | "업데이트 하고부터 안 떠요" | 원인 3, 또는 A토큰 미등록 | iOS 구독자 973명이 A토큰 없음 |
| C | cha***@gmail.com | 카드 겹침 + 스코어 0:0 고정 | 원인 1 + 원인 2 | 이 경기만 약 2,700명 |

---

## 원인 0 — 구독 직후 따라잡기가 파드 분리 이후 항상 no-op (이 PR 이 고치는 것)

`LiveActivityCatchUpService` 는 `LiveStateStore`(인메모리) 만 보고 "지금 라이브인가"를 판정했다.
구독 API 는 **웹 파드**가 처리하고, 웹 파드는 `APP_SCHEDULING_ENABLED=false` 라 라이브 폴링을
돌리지 않으므로 그 store 는 **영구히 비어 있다.** `liveGameOf(matchId)` 가 항상 empty →
`ifPresent` 미실행 → 로그도 발송도 없다.

```
2026-08-12 19:56  #368  구독 직후 카드 생성 도입      ← 단일 파드에선 정상
2026-08-22 12:49  #442  스케줄러를 별도 파드로 뗀다   ← 이 순간부터 무동작
2026-08-23 16:27  #466  같은 원인의 세트 상태 버그 수정
```

### 실측 (2026-08-23 NS vs BFX, matchId `115548147900750229`)

```
19:21:47  set1 start
19:21:58  p2s set1 발송 94건            → 카드 생성, B토큰 id=3607 (19:21:51)
          사용자가 잠금화면에서 카드 삭제
20:12:02  set2 start
20:12:07  p2s set2 발송 65건            → 유령 B토큰 때문에 대상에서 제외
20:17:20  DELETE /api/mobile/me/match-subscriptions/115548147900750229
20:17:21  구독 행 재생성 (id=2885)
          → "구독 직후 카드 생성" 로그 0줄, 발송 0건
```

B토큰 3607 이 `active=1` `updated_at 19:21:51` 로 그대로 남아 있었다 — `startCardForMember` 는
제일 먼저 `closePreviousCard` 를 부르므로, 그게 안 돌았다는 것이 `submit()` 미도달의 증거다.

```
nar-web 최근 2시간 로그의 live-discovery|live-notify 매칭:  0건
```

**영향이 큰 이유**: 재구독은 케이스 A·B·C 전부에서 **사용자가 스스로 쓸 수 있는 유일한 복구
수단**이었다. 그게 하루 동안 죽어 있었다.

### 같은 클래스 전수 조사

| `LiveStateStore` 소비자 | 웹 도달 | 상태 |
|---|---|---|
| `MobileScheduleService` | O | #466 으로 수정 (DB 신선도 폴백) |
| `LiveActivityCatchUpService` | O | **이 PR 로 수정** |
| `LiveController.getLiveGames()` | O | 항상 빈 배열 (디버그용 엔드포인트, 사용자 영향 없음) |
| `LiveStateQueryService.getLatestState` | O | DB 폴백 있음 → 동작. 단 웹에서는 항상 분 단위 스냅샷 경로라 신선도가 낮다 |
| `LiveFrameProcessor`·`LivePollingScheduler`·`LiveBackfillService` | X | 스케줄러 전용, 정상 |

라이브 스코어가 멀쩡했던 이유가 `getLatestState` 의 DB 폴백이다. 따라잡기만 폴백이 없었다.

---

## 원인 1 — 세트 시작 일괄 경로에 카드 정리가 없다

```
LiveActivityPushService.java:157  startCards()            ← closePreviousCard 없음
                          :200  startCardForMember()    → closePreviousCard(...)
                          :220  closePreviousCard 정의
                          :61   RECENT_START_WINDOW = 30초
```

중복 방지가 `findStartTargets` 의 `NOT EXISTS(active LiveActivityToken)` 하나에 걸려 있는데,
그 B토큰은 앱이 실행돼야 등록된다. 결과:

```
B토큰 있음 → 다음 세트는 p2s 제외, 기존 카드 갱신          (정상)
B토큰 없음 → 다음 세트에 카드를 또 만든다                  (스택)
            → 옛 카드는 갱신도 종료도 불가                 (생성 시점 스코어에 고정)
```

`claimStart` 중복 창은 30초라 세트 간격(약 20분)에 무력하다.

### 실측 (T1 vs HLE, matchId `115548147900553481`, 0:2 종료)

```
17:11:48  push-to-start set=1  발송 3120건, 죽은 토큰 28건
18:01:32  end=false            발송  334건        ← B토큰 보유자
18:01:40  push-to-start set=2  발송 2702건        ← 나머지에게 두 번째 카드
```

3120 − 334 − 28 ≈ 2758 ≈ 2702. **약 2,700명이 카드 두 장을 갖게 됐고 1세트 카드는 0:0 에 고정된다.**
p2s 카드는 생성 시점 `LeagueMatch` 스코어를 싣는다 — 1세트 시작은 `0:0`. 신고 C의 "매 세트 다
끝나도 계속 0:0" + "똑같은 게 한꺼번에 겹쳐져있다" 가 정확히 이 그림이다.

---

## 원인 2 — A토큰(p2s) 로테이션이 정리되지 않는다

```
회원별 active A토큰:  1개 2818명 | 2개 215 | 3개 150 | 4개 35 | 5개 4 | 6개 2
active 3859행 vs 실제 회원 3224명  →  낡은 행 약 635개
T1/HLE 대상만: 회원 2601명 / 토큰 3131개 → 530개 잉여
```

앱 업데이트로 토큰이 로테이션되면 새 행이 insert 되고 옛 행은 `active=1` 로 남는다. 로테이션된
토큰은 410 을 안정적으로 주지 않아 스스로 죽지도 않는다. 한 세트 시작에 같은 기기로 두 번 발송돼
원인 1의 스택을 한 겹 더 얹고, 발송 카운트도 오염시킨다.

---

## 원인 3 — 앱이 살아있는 카드를 스스로 지운다

`lib/repository/live_activity/live_match_activity_controller.dart` (릴리스 태그 기준):

```dart
// :156  _loadTeamSubs — 실패를 빈 목록으로 접는다
} catch (e) { return cached ?? const []; }

// :111  _todayMatchIdsOfSubscribedTeams
if (codes.isEmpty) return const [];     // ← "성공했고 후보 없음" (null 이 아니다)

// :39   dismissStaleCards
final subscribedIds = await _subscribedMatchIds() ?? const <String>{};   // null(실패)도 접는다
if (await _anyOngoing(subscribedIds)) return;
final todays = await _todayMatchIdsOfSubscribedTeams();
if (todays == null) return;             // :45 — 도달하지 않는다
await _service.endAll();                // :51 — 살아있는 카드 전멸
```

같은 파일이 두 곳에서 이걸 금지한다 — "빈 목록(대상 없음)과 실패를 구분해야", "잘못 닫는 건
복구 불가라 의심스러우면 두는 쪽이 싸다". `_loadTeamSubs` 안에서만 그 구분이 사라지고, 두 후보
경로가 **둘 다 인증을 타므로 같은 원인으로 동시에 비어버린다**(독립 실패가 아니다).

`secure_storage.dart` 가 1.0.17 부터 모든 읽기 실패를 예외로 올리게 바뀌어
(`readOrThrowIfUnavailable`) 이 경로의 도달 가능성이 오히려 높아졌다.

실행 시점 (`lib/main.dart`):

```
:158  dismissStaleCards();              ← 먼저 돈다
:162  observePushToStartToken();
:179  resumed → dismissStaleCards();    ← 포그라운드 복귀마다
```

**iOS 가 p2s 로 카드를 만들어 앱을 깨우면, 토큰 관찰을 시작하기도 전에 그 카드를 지울 수 있다.**
앱은 삭제를 서버에 알리지 않으므로 B토큰이 유령으로 남아 그 매치 내내 p2s 가 막힌다.

### B토큰 31% 는 원인 3 이 아니라 플랫폼 한계다

카드 갱신은 앱을 열지 않아도 되게 설계돼 있다 — `LiveActivityPlugin.swift:297-308` 의
`activityUpdates` 스트림이 p2s 카드까지 잡는다. 실제로 잡히기도 한다:

```
T1 vs HLE B토큰 생성 시각
  17:11  129건   ← 세트1 p2s(17:11:48) 순간 버스트 = 3120건 대비 4.1%
  18:01  366건   ← 세트2 p2s(18:01:40) 순간 버스트 = 2702건 대비 13.5%
  그 외   분당 1~10건의 긴 꼬리 (사용자가 앱을 열 때)
```

버스트가 있으니 백그라운드 깨우기는 **일어난다**. 다만 5~14% 뿐이고 세트2가 세트1의 3배인 것은
세트1 동안 앱을 쓴 사람이 워밍업돼 있었기 때문이다 — iOS 의 실행 예산 정책에 걸린다.
**서버로 풀 수 없는 천장이다.**

---

## 케이스별 상세

### A. 카드가 중도에 사라지면 그 경기 내내 복구 불가

```sql
-- T1 vs HLE
select id, member_id, match_id, active, created_at from live_activity_token
where member_id = 10 and match_id = '115548147900553481';
-- id=2852 active=1 created_at=17:11:44   ← 카드는 없는데 토큰은 살아있다

update live_activity_token set active = 0 where id = 2852;   -- 수동 복구
```

18:02 에 복구했지만 2세트가 마지막 세트(0:2)여서 발동하지 못했다. 복구가 "다음 세트 시작"에만
걸려 있는 것 자체가 한계이고, 원래 있어야 할 자력 복구 수단(재구독)은 원인 0 으로 죽어 있었다.

매치를 넘어 새지는 않는다 — 지난 경기의 `active=1` B토큰 0건. 종료 스윕은 정상.

### B. "업데이트 하고부터 안 떠요"

```
iOS + set_start_enabled 구독자:  A토큰 있음 2947명 / 없음 973명 (그중 오늘 앱 켠 사람 88명)
A토큰은 있는데 set_start_enabled 구독이 없는 회원 263명
팀구독 set_start_enabled=0 인 회원 423명
```

1.0.18+40 이 2026-08-22 01:14 릴리스라 시점이 맞는다. 후보 원인 4개:

1. **원인 3** — 카드가 생성 직후 앱에 의해 삭제
2. iOS 17.2 미만 — p2s 토큰 미발급
3. 설정에서 실시간 현황 OFF — `observePushToStartToken`(`LiveActivityPlugin.swift:257`)은
   `areActivitiesEnabled` 를 확인하지 않는다(`isSupported()`(:89)에만 있고 이 경로는 안 부른다).
   앱도 서버도 이 상태를 모른다
4. 앱 실행 시점 로그아웃 — 등록 시도가 그 실행에서 삼켜진다

4번은 영구 고착이 아니다. `_lastStartToken`(`live_activity_push_token_repository.dart:62`)이
인메모리라 콜드 실행마다 재시도된다(주석 `:60` — "앱 실행마다 같은 값이 다시 흘러나온다").
남는 구멍은 "실행 직후엔 로그아웃이었고 그 세션에서 로그인한" 경우다.

**973명을 이 4개로 쪼갤 방법이 없다.** 서버는 앱 버전·iOS 버전을 모른다 — `member_device` 에
`platform` 만 있고, 앱은 버전 헤더도 User-Agent 도 보내지 않는다.

### C. 카드 겹침 + 스코어 0:0 고정

원인 1 + 원인 2 의 합성.

---

## 구독/재구독 경로의 나머지 조건

원인 0 을 고친 뒤에도 남는 게이트:

| 조건 | 실패 시 |
|---|---|
| 최신 프레임 3분 이내(또는 인메모리 활성 게임) | **세트 사이 휴식엔 안 뜬다.** 다음 세트 p2s 가 커버한다지만 **마지막 세트 뒤엔 다음 세트가 없다** |
| A토큰 `active=1` | 973명은 안 뜬다 |
| `claimStart` 30초 창 | 세트 시작 직후 30초 안에 재구독하면 스킵 |
| `setStartOrTrue()` 토글 | OFF 면 스킵 |

### 구독 취소가 카드를 닫지 않는다

```java
// MobileMatchSubscriptionService.java:89-91
public void unsubscribe(Long memberId, String matchId) {
    subscriptionRepository.deleteByMemberIdAndMatchId(memberId, matchId);
}
```

`MobileTeamNotificationService.delete()` 도 동일. B토큰이 `active=1` 로 남아 취소한 사람의 카드가
계속 갱신된다. 재구독하면 `closePreviousCard` 가 치우니 자기치유되지만, 취소한 채로 두면 남는다.

---

## 부수 확인

- **스윕은 돌지만 앱을 열어야 닿는다.** `LiveActivityOrphanCardSweeper` 가 5분마다 끝난 매치를
  정리하고, 종료 1시간 뒤에도 소량 발송이 이어진다(18:51 6건 → 19:22 2건 → 19:54 6건).
  뒤늦게 앱을 열면 남은 카드의 B토큰이 등록되고 다음 스윕이 닫는다 — 앱을 안 여는 사람의 스택
  카드는 서버가 손댈 수 없다.
- 이날 16:27 에 #466 이 배포돼 스케줄러 파드가 16:29 재기동했다. **17:00 경기의 경기 창(시작
  1시간 전) 안이었다.** 변경 범위가 카드 경로와 무관하고 세트1 발송(3,120건)도 정상이었지만,
  규칙상 피해야 하는 창이었다.

## 문의자 식별 불가

두 이메일 모두 `member.email` 에 없다(6,496행, 부분일치 0건). `member_social` 은
`provider`/`provider_id` 만 있고 이메일 컬럼이 없다. 문의 폼에 계정 이메일이 아닌 것을 적었거나
비회원이다. **비회원이면 p2s 자체가 불가능하다.** 답장에서 가입 이메일 또는 앱 내 닉네임을 받아야
확정된다.

---

## 수정 계획

| 순위 | 대상 | 무엇 | 상태 |
|---|---|---|---|
| 0 | back | 따라잡기의 라이브 판정을 인메모리 ∪ DB 로 (원인 0) | **이 PR** |
| 1 | back | p2s 카드를 매치당 1장으로 — `claimStart` 를 매치 범위로 넓혀 세트마다 재생성하지 않는다 | 미착수. 원인 1의 겹침을 실제로 없애는 유일한 안 |
| 2 | mobile | `_loadTeamSubs` 실패를 `null` 로 올려 `dismissStaleCards` 가 중단하게 (3줄) | 미착수 |
| 3 | mobile | `dismissStaleCards` 를 `observePushToStartToken` 뒤로 | 미착수. 2번 종속 |
| 4 | back | A토큰 로테이션 정리 — 새 A토큰 등록 시 이전 행 `active=0` | 미착수 |
| 5 | back | 구독 취소 시 그 경기 카드 닫기 + B토큰 비활성화 | 미착수 |
| 6 | mobile+back | 관측: `X-App-Version`·iOS 버전 전송 → `member_device` 저장, `iOS 구독자 중 A토큰 없음` 게이지 | 미착수 |
| 7 | mobile | `areActivitiesEnabled == false` 를 서버에 보고 | 미착수 |
| 8 | widget | `stale-date` 로 "갱신 안 됨" 표시 | 미착수. 0:0 고정의 정직한 표현 |
| 9 | back | 세트 시작 카드가 FCM 팬아웃 뒤에 매달려 46~69초 늦다 (`LivePollingScheduler.java:417`) | 미착수 |

**`startCards` 에 `closePreviousCard` 를 더하는 것은 C를 거의 못 고친다** — 닫을 B토큰이 없는
사람이 정확히 C를 겪는 집단이다. 그래서 1순위를 "매치당 1장"으로 뒀다.

---

## 문의 답장 초안

### 케이스 B

> 아래 3개를 순서대로 확인해 주세요.
>
> 1. **아이폰 설정 > NAR > 실시간 현황(Live Activities)** 이 켜져 있는지 — 업데이트 후 꺼지는 경우가 있습니다
> 2. 앱 **알림 설정에서 응원 팀의 '세트 시작 알림'** 이 켜져 있는지
> 3. 위 둘을 켠 뒤 **앱을 완전히 종료**(앱 스위처에서 위로 밀기)하고 **로그인된 상태로** 다시 실행해 10초 정도 켜 두기
>
> 잠금화면 카드 등록이 앱 실행 시점에 이뤄지기 때문에 3번이 필요합니다. 다음 경기 세트 시작 때
> 카드가 다시 뜹니다. 그래도 안 되면 **iOS 버전**(17.2 이상 필요)과 **가입에 사용한 이메일 또는
> 앱 내 닉네임**을 알려주세요.

### 케이스 C

> 정확히 저희 쪽 버그입니다. 원인은 두 가지입니다.
>
> 1. **겹쳐 보이는 것** — 세트가 시작될 때마다 카드를 새로 만드는데, 앱이 실행되지 않은 기기에서는
>    이전 세트 카드를 닫을 수 없어 그대로 쌓입니다.
> 2. **0:0 고정** — 쌓인 옛 카드는 갱신 대상에서 빠져 만들어진 시점(1세트 시작 = 0:0)에 멈춰
>    있습니다. 겹친 카드 중 가장 최근 것만 스코어가 맞습니다.
>
> 임시 회피: 경기 중 **앱을 한 번 열어두면** 이후로는 카드가 새로 쌓이지 않고 기존 카드가
> 갱신됩니다. 쌓인 카드는 왼쪽으로 밀어 지울 수 있습니다.
>
> 다음 배포에서 세트마다 카드를 새로 만들지 않도록 고칩니다. 스크린샷이 원인 파악에
> 결정적이었습니다. 감사합니다.

---

## 참고

- 로그: `kubectl -n nar logs <nar-scheduler-pod> --since=2h | grep "live-activity"`.
  따라잡기는 **웹 파드**를 봐야 한다 — `kubectl -n nar logs <nar-web-pod> | grep "구독 직후"`
- prod DB: 맥미니 MySQL, `nar-env` 시크릿의 `DB_PASSWORD`
