# 라이브 경기 최종 검증 체크리스트

이 문서는 실제 LCK 경기가 진행될 때 라이브 수집 안정화 작업을 최종 검증하기 위한 체크리스트다.
단위 테스트와 과거 프레임 시뮬레이션으로 확인할 수 없는 운영 환경 동작만 다룬다.

## 사전 조건

- `lolesports.live.enabled=true`
- `LOL_ESPORTS_KEY`가 유효하게 설정됨
- Flyway `V35__Create_live_streaming_tables.sql` 적용 완료
- 애플리케이션과 MySQL이 정상 실행 중
- 경기 시작 전 `/api/live/games`가 빈 목록 또는 기존 경기 없이 정상 응답

## 경기 시작

- 경기 시작 후 discovery 주기 안에 `/api/live/games`에 게임이 나타난다.
- `gameId`, `matchId`, 리그명, 양 팀 이름이 실제 경기와 일치한다.
- `/api/live/games/{gameId}`의 `frameTimestampUtc`가 새로운 프레임마다 증가한다.
- 동일한 `frameTimestampUtc`가 저장 큐에 반복 적재되지 않는다.
- `/api/live/queue`의 `rejectedTasks`가 증가하지 않는다.

## 경기 진행

- 일정 API의 경기 상태가 `inProgress`로 갱신된다.
- 세트가 종료될 때 양 팀 스코어가 일정 API에 반영된다.
- 일정 캐시 때문에 이전 상태나 점수가 계속 노출되지 않는다.
- 선수 이름, 챔피언, 포지션, KDA, CS, 골드, 아이템, 룬이 실제 화면과 일치한다.
- 드래곤, 바론, 포탑, 억제기 이벤트가 중복 없이 시간순으로 기록된다.
- 네트워크 응답이 일시적으로 비어도 애플리케이션 오류나 잘못된 빈 스냅샷이 발생하지 않는다.

## 세트 전환

- 다음 세트 시작 시 새로운 `gameId`가 자동 발견된다.
- 이전 세트의 최신 상태가 새 세트 상태를 덮어쓰지 않는다.
- 양 팀의 블루/레드 진영과 선수 구성이 새 세트 기준으로 표시된다.

## 경기 종료

- 최종 스코어와 `completed` 상태가 일정 API에 반영된다.
- 종료 직후 마지막 상태 갱신이 누락되지 않는다.
- stale 임계 시간이 지나면 종료된 게임이 `/api/live/games`에서 제거된다.
- 분 단위 스냅샷과 오브젝트 이벤트가 DB에 남아 조회 가능하다.

## 이상 징후 확인

- `Live discovery failed`
- `Live polling failed`
- `Removing game ... after ... consecutive polling failures`
- 저장 큐의 `rejectedTasks` 증가
- 프레임 시각 역행 또는 장시간 정지
- 세트 전환 후 이전 `gameId`가 계속 활성 상태로 유지

실제 경기 검증이 끝나면 경기 ID, 확인 시간, 발견한 문제와 로그를 이 문서 하단에 추가한다.
