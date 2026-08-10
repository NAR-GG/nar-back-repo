# 알림 잠자기(방해금지 시간) 설계

작성일: 2026-08-10
브랜치: `feat/notification-quiet-hours`
UI 목업: https://claude.ai/code/artifact/7689e6d6-0c07-48fc-aae0-b9739097cb15

## 배경

앱 문의: "밤에도 알림이 너무 많이 와서 잠자기 모드가 있으면 좋겠다."

솔랭 알림은 새벽이 피크다. 선수 한 명이 한 번 앉으면 3~4판을 연속으로 돌리고, 감지 대상 선수가
여럿이라 새벽에 알림이 겹친다. 현재 유저가 조절할 수 있는 건 선수 구독 on/off뿐이라
"이 선수 알림은 받고 싶지만 새벽엔 조용했으면"을 표현할 방법이 없다.

## 타 앱 조사

| 앱 | 있음 | 형태 |
|---|---|---|
| Slack | O | DND 스케줄 — 매일/주중/커스텀 + 시작·종료 시각. 하루 1회 강제 관통 허용 |
| Instagram | O | Quiet mode — 시간대 지정 |
| YouTube | O | "알림 사용 안 함 시간대" — 소리·진동만 죽이고 알림은 남김 |
| OS | O | Android 방해금지/취침 모드, iOS 수면 집중모드 |
| OP.GG 앱 | 확인 안 됨 | 카테고리별 on/off만. 커뮤니티에 "알림 끄기 설정 만들어주세요" 글 존재 |

두 갈래로 나뉜다.

- **소리만 죽임**(YouTube) — 알림은 알림함에 쌓인다. 정보 손실 없음.
- **아예 안 보냄**(Slack DND) — 놓침이 생긴다.

**무음 쌓기를 택한다.** 알림함(`MemberNotification` 피드)이 이미 있어서 조용히 쌓아둘 자리가 있고,
알림을 버리면 "왜 알림이 안 왔냐" 문의가 새로 생긴다.

또한 알림 종류를 가리지 않는다 — 선례(Slack DND, Instagram Quiet mode)가 모두 전체 적용이고,
"정한 시간엔 조용하다" 한 문장이 유저가 이해할 수 있는 유일한 모델이다.
LCK 정규시즌 경기는 17:00~22:00 KST라 기본 잠자기 시간(01:00~08:00)과 겹치지 않는다.
겹치는 건 국제전(MSI/월즈) 한국 새벽 경기뿐이고, 연간 2~3주다. 이때는 유저가 시간을 조정한다.

## 삭제할 죽은 코드

`PlayerSoloRankPushService`가 FCM 토픽 `all_solo_rank`로도 발송한다
(`:99-109`, 커밋 `6140d2fd`, "전체 선수 솔랭 알림" 기능). 그런데 **플러터 앱에 `subscribeToTopic`
호출이 하나도 없다** — 이 토픽 발송은 아무도 받지 않는다. 기능도 현재 미제공이다.

이 발송을 지우면 부수 효과가 두 개 있다.

1. 잠자기의 최대 걸림돌이 사라진다. 토픽 구독 상태는 FCM에만 있어 서버가 유저별 시각을 알 수 없으므로,
   토픽 경로가 살아 있으면 잠자기 설정을 무시하고 소리가 새서 기능이 거짓말이 된다.
2. `FirebaseMobilePushGateway`의 중복 빌더 2개(`:65-80` 토픽용, `:92-107` 멀티캐스트용)가 1개로 줄어
   무음 분기를 한 곳만 고치면 된다.

`MobilePushGateway.sendToTopic`과 그 테스트(`PlayerSoloRankPushServiceTest:81`)도 함께 지운다.

## 데이터 모델

`member`에 컬럼 3개. 1:1이라 별도 테이블을 만들지 않는다.

```sql
-- V32__add_member_quiet_hours.sql
ALTER TABLE member
  ADD COLUMN quiet_hours_enabled TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN quiet_start_time TIME NOT NULL DEFAULT '01:00:00',
  ADD COLUMN quiet_end_time   TIME NOT NULL DEFAULT '08:00:00';
```

- `TIME` + `LocalTime`. 분 단위를 버리면 오히려 플러터에서 `TimeOfDay.minute`를 버리는 코드가 늘어난다.
- 입력은 5분 스텝. 취침 시간에 1분 정밀도는 필요 없고, 휠이 12행으로 짧아진다.
- 요일별 설정 없음. 주말에도 잠은 잔다.
- 유저별 타임존 없음. KST 고정. 교민 문의가 실제로 오면 그때 컬럼을 더한다.

### 기본값 OFF는 안전 요구사항이다

Android 무음은 새 알림 채널을 필요로 하고(아래 참조), 채널은 앱이 만든다. 서버는 기기의 앱 버전을
모른다 — `member_device`에 버전 컬럼이 없다. 구버전 앱에 존재하지 않는 `channel_id`를 보내면
Android가 알림을 띄우지 못해 **알림이 유실된다.**

`quiet_hours_enabled` 기본값을 0으로 두면, 이 설정은 신버전 앱에서만 켤 수 있으므로
"켜져 있다 = 신버전이다"가 성립한다. 설정 자체가 버전 게이트라서 버전 컬럼을 만들 필요가 없다.

## 판정 로직

```java
// ponytail: KST 고정. 유저별 타임존은 교민 문의가 실제로 오면 그때.
LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
boolean quiet = start.isBefore(end)
        ? !now.isBefore(start) && now.isBefore(end)   // 같은 날 구간
        : !now.isBefore(start) || now.isBefore(end);  // 자정 넘김
```

- **판정은 Java에서 한다. SQL로 시각을 비교하지 않는다.** 커밋 `914d932`에서 대시보드 시간 버킷이
  DB 세션 타임존에 따라 9시간 밀린 것과 같은 함정이다. `TIME` 컬럼은 저장 전용이다.
- `start == end`는 API에서 400으로 거부한다. 막지 않으면 위 식이 else 분기로 빠져 24시간 무음이 되는데,
  유저는 그걸 의도하지 않았고 증상이 조용해서 원인을 못 찾는다.

## 플랫폼별 무음 처리

### iOS — 서버만으로 된다

`Aps`에서 `setSound`를 생략하고 `interruption-level: passive`를 넣는다. 화면을 켜지 않고
배너도 띄우지 않으며 알림함에만 남는다. 앱 변경 없음.

앱이 포그라운드일 때는 `setForegroundNotificationPresentationOptions(sound: true)`
(`fcm_service.dart`)로 소리가 날 수 있지만, 포그라운드면 유저가 깨어 있으므로 무해하다.

### Android — 앱 배포가 필요하다

Android O+ 는 **알림 채널 설정이 서버 payload보다 우선한다.** 서버가 `sound`를 비우고 priority를
낮춰도 채널 importance가 high면 시스템이 채널 설정대로 소리를 낸다. 그리고 채널은 한 번 만들어지면
앱이 코드로 importance를 바꿀 수 없다 — 유저가 시스템 설정에서 바꾸는 것만 가능하다.

현재 앱 채널은 하나다.

- `warding_high_importance` / `Importance.high` (`fcm_service.dart:32`)
- 매니페스트 기본 채널도 같은 값 (`AndroidManifest.xml:71`)

필요한 작업:

| 층 | 작업 |
|---|---|
| 앱 | `warding_quiet` 채널 추가 (`Importance.low`, 소리 없음) |
| 앱 | 포그라운드 표시 경로(`fcm_service.dart:195`)가 `Importance.high` 하드코딩 — 무음이면 새 채널로 분기 |
| 서버 | 무음이면 `android.notification.channel_id`를 `warding_quiet`으로 지정 |

**이것이 이 기능의 병목이다.** 백엔드는 반나절이지만 Android 무음은 스토어 심사와 유저 업데이트
확산을 기다려야 한다. 앱을 업데이트하지 않은 유저는 계속 시끄럽고, 백엔드만 먼저 배포해서
조기 완화할 우회로가 없다.

## 발송 경로별 적용

### 솔랭 — 회원 단위 루프라 간단

`PlayerSoloRankPushService.sendToMember`(`:111`)가 이미 회원별로 돌므로 판정만 끼운다.

### 경기 — 팬아웃을 2그룹으로 분할

`TeamLiveEventPushService.fanOutBatched`(`:347`)는 **전 구독자 토큰을 한 멀티캐스트로 몰아 보낸다.**
주석에 실측 근거가 있다: 2026-07-29 T1 vs KT에서 구독자 약 1,500명 개별 발송이 이벤트당 8~18분
걸려 마지막 구독자는 세트가 끝난 뒤에 세트 시작 알림을 받았고, 그 시간 동안 라이브 관측까지 멈췄다.

이 배치를 회원별로 되돌리면 안 된다. 구독자를 **잠자기 걸린 집합 / 안 걸린 집합 2그룹**으로 나눠
멀티캐스트를 2번 보낸다. 왕복 1회 → 2회로 끝이고 O(1)이 유지된다.

**진짜 비용은 N+1이다.** `device.getMember().getId()`는 프록시를 초기화하지 않지만 잠자기 필드를
읽으면 초기화된다 — 1,500명이면 쿼리 1,500방이다. 기기 조회 3곳
(`findActiveDevicesBySubscribedMatchId` / `...TeamId` / `...PlayerId`)에 fetch join을 붙이는 대신,
**회원 id 집합으로 잠자기 설정만 한 방에 조회하는 projection 쿼리 1개**를 쓴다.
기존 쿼리를 건드리지 않아 회귀 위험이 적다.

## 게이트웨이 변경

`MobilePushMessage`에 `silent` 필드를 더한다. 기존 3-인자 생성자를 남겨 `silent=false`로 위임하면
호출처 대부분이 무수정으로 남는다.

`FirebaseMobilePushGateway`는 `silent`일 때:

- `Aps`에서 `setSound` 생략 + `interruption-level: passive`
- `AndroidConfig.Notification`에 `channelId("warding_quiet")`, priority `NORMAL`

## API

```
PUT /api/mobile/members/me/quiet-hours
{ "enabled": true, "startTime": "01:00", "endTime": "08:00" }
```

- `enabled=true`인데 `startTime == endTime`이면 400
- 분이 5의 배수가 아니면 400 (클라가 5분 스텝이므로 서버는 계약 확인만)
- 현재 설정은 기존 회원 정보 조회 응답에 필드로 얹는다 — 엔드포인트를 새로 만들지 않는다

## 앱 UI

마이페이지 `subscription_alarm_section.dart` 아래 카드 하나. 목업 참조.

- OFF: 토글 1행 + 안내. 시간 행은 숨긴다.
- ON: 구분선 아래 `시작` / `종료` 2행. 각 행 우측은 값 + chevron.
- 시각 선택: `AppBottomSheet` + 5분 스텝 휠. `showTimePicker`가 코드는 적지만 앱이 바텀시트로
  톤을 통일해 두었고 머티리얼 다이얼로그 하나가 튄다. 5분 스텝이면 휠 조립 비용이 거의 없다.
- 팀 알림 행이 쓰는 좌측 들여쓰기(`EdgeInsets.fromLTRB(60,…)`)는 팀 로고 정렬용이므로 쓰지 않는다.
  로고가 없으니 들여쓸 근거가 없다. 좌우 20으로 맞춘다.
- 비회원은 카드를 숨긴다. 팀 알림 섹션의 기존 패턴(`subscription_alarm_section.dart:53`)과 같다.

문구(ko/en 양쪽 `app_ko.arb` / `app_en.arb` 필요):

| 위치 | 문구 |
|---|---|
| 섹션 제목 | 알림 잠자기 / Quiet hours |
| 토글 | 잠자기 사용 / Use quiet hours |
| 시간 | 시작 · 종료 / From · To |
| OFF 안내 | 켜면 정한 시간엔 알림이 소리 없이 알림함에만 쌓입니다. |
| ON 안내 | 오전 1:00부터 오전 8:00까지 모든 알림이 소리 없이 알림함에만 쌓입니다. 새벽 국제전을 챙기려면 이 시간을 조정하세요. |
| 오류 | 시작과 종료가 같으면 안 됩니다. 다른 시간을 골라주세요. |

ON 안내에 실제 설정 시각을 넣는다. "무음으로 쌓인다"는 동작이 눈에 보이지 않아 유저가 껐다고
착각하기 쉽다. 국제전 문장은 잠자기가 모든 알림에 걸린다는 걸 미리 알려 "왜 경기를 놓쳤냐"를 막는다.

## 알림함

무음이어도 피드에는 그대로 기록한다. `recordFeed`는 발송 성공 시 기록하므로 변경 없다.
"소리 없이 알림함에만 쌓인다"는 약속이 여기서 지켜진다.

## 테스트

1. **wrap-around 판정** — `01:00~08:00`에 `00:59`/`01:00`/`07:59`/`08:00`, `23:00~08:00`에 `23:30`/`12:00`
2. **`silent`가 게이트웨이까지 전달되는지** — 잠자기 시간대 회원에게 `silent=true` 메시지가 가는지
3. **팬아웃 분할** — 잠자기 회원과 아닌 회원이 섞인 구독자 집합에서 멀티캐스트가 2번, 각 그룹의
   토큰이 올바르게 갈리는지
4. **`start == end` 400**

## 검증 경로 — 별도 준비가 필요하다

무음이 실기기에서 진짜 조용한지는 실기기로만 확인된다. 그런데 로컬은 스케줄러가 OFF이고
FCM 자격증명이 prod와 공유돼서(`CLAUDE.md` 스케줄러 전역 스위치 항목) 테스트 푸시가 실유저에게 갈 수 있다.

필요한 것:

- 내 기기 토큰만 지정해 쏘는 백오피스 트리거 (기존에 있는지 확인)
- 잠자기 시각 판정에 시각 주입 — 새벽까지 기다리지 않으려면 `Clock` 빈 또는 테스트 파라미터

## 범위 밖

**연속 판 묶기.** 같은 선수를 30분 내 재감지하면 알림을 스킵하는 총량 제한. 문의 원문이 "밤에도"라
시간대 대응을 먼저 하지만, 체감 개선은 이쪽이 더 클 수도 있다. 잠자기 배포 후 문의가 계속 오면 그때.

**야간 다이제스트.** 아침에 "밤에 12판 있었어요" 1건으로 묶어 보내기. 알림함이 이미 그 역할을 한다.
