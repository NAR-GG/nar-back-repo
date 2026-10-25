# 모바일 일정 API `utcOffset` — 해외 사용자 현지 시간 표시

작성일: 2026-10-25 · 대상: `GET /api/mobile/schedules`, `GET /api/mobile/schedules/calendar`, `GET /api/mobile/matches`, `GET /api/mobile/matches/{matchId}`

CX 문의("해외에 사는데 한국 시간으로 떠서 불편합니다")에 대응한다. 앱은 기기 IANA 시간대 이름을
읽을 수단이 없어서(네이티브 플러그인 = 스토어 릴리즈) **UTC 오프셋(분)** 만 보내고, 서버가 그 오프셋
기준으로 날짜를 묶고 시각을 내려준다.

## 파라미터

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `utcOffset` | int | `540` (KST) | UTC 기준 분, 동쪽이 `+`. 예: KST `540`, PDT(LA 여름) `-420`, CET `60`, IST `330`. 허용 `-720..840`, 범위 밖 400 |

네 엔드포인트 모두 같다. 미전송이면 기존과 바이트 단위로 같은 응답이다.

## 의미

- 요청 날짜·월(`date`, `month`, `from`, `around`)은 그 오프셋의 날짜다. 하루 = `[그 날 00:00, 다음날 00:00)` at offset.
- 응답의 날짜 필드(`MobileScheduleListResponse.date`, `matches[].date`, 단건 `date`, 캘린더 `dates[].date`)와
  `scheduledTime`(HH:mm)이 그 오프셋 기준이다. 필드 추가·이름 변경은 없다.
- 커서(`cursor`/`nextCursor`/`prevCursor`)는 UTC 절대 시각이라 오프셋과 무관하다. 다만 이어받을 때
  `from`/`around` 해석과 응답 표시가 맞도록 **같은 `utcOffset` 을 계속 보낸다**.
- 캘린더 서버 캐시(`mobileScheduleCalendar`)는 오프셋을 키에 넣는다. HTTP `Cache-Control` 은
  쿼리스트링이 URL 에 포함되므로 그대로 분리된다.

## 예시

KST 2026-10-26 04:00 경기(월즈)를 LA(PDT) 사용자가 본다:

```
GET /api/mobile/schedules?date=2026-10-25&league=WORLDS&utcOffset=-420
→ { "date": "2026-10-25", "matches": [ { "date": "2026-10-25", "scheduledTime": "12:00", ... } ] }
```

## 한계

- 오프셋은 요청 시점 값 하나다. DST 전환일을 넘는 구간(예: 11월 캘린더의 전환일 이후)은
  한 시간 어긋날 수 있다. 앱이 IANA 이름을 보낼 수 있게 되면 `timeZone` 파라미터로 확장한다.
- 단건(`/matches/{matchId}`)은 경기 상세 화면이 `date`·`scheduledTime` 을 그대로 보여주므로 목록과
  같은 `utcOffset` 을 보내야 한다. 안 보내면 KST 라 목록(현지)과 상세(KST) 시각이 어긋난다.
