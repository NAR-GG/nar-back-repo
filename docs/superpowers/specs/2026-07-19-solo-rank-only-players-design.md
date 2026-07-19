# 솔랭 전용 선수 지원 설계

## 배경 / 목적

은퇴·비현역 프로(예: Deft, Rascal, Peanut)를 **솔로 랭크 알림 대상**으로 추가하려는 요구가 있다.
현재 앱은 "선수 = LCK 출전 기록 보유자" 전제로 동작해, 다음 게이트가 이를 막는다.

1. Riot 계정 sync 대상 = `findPlayersByLeagueName("LCK")` — 출전 기록 없으면 puuid 미생성 → 감시 불가
2. 선수 구독 자격 = LCK **2026** 출전 기록 (`findLckPlayerOptions*`) — 미출전이면 목록·구독·표시 전부 차단
3. 백오피스 선수 수정 = `hasLeagueParticipation("LCK")` 강제

현황 (운영 DB 확인):
- **Peanut** (player_id 717): `players` 존재 + `PlayerRiotAccount`(puuid, enabled, primary, KR) 보유 → 이미 솔랭 감시 중. 구독 노출만 필요.
- **Deft / Rascal**: `players` 행 자체 없음 → 모든 파이프라인 스코프 밖.

외부 솔랭 소스(TrackingThePros)는 세 선수 모두 활성 KR 솔랭 계정 게시 중:
- Deft `Deft#8366` (Master), Rascal `Rascal#1231` (Master), Peanut `Peanut#kr11` (Diamond II).
- 앱 파이프라인: TTP 크롤(05:30) → `Player.gameAccounts` → Riot API(06:00) → `PlayerRiotAccount(puuid)`.

## 목표

LCK 출전 기록이 없는 선수도 **솔랭 전용**으로 등록·감시·구독할 수 있게 한다.
초기 대상은 Deft, Rascal (Peanut은 감시 중이라 구독 노출만).

## 비목표 (YAGNI)

- `Player`에 신규 컬럼(active/soloOnly 등) 추가 — 안 함
- 별도 관리자 UI 화면 — 안 함(엔드포인트만)
- 크롤러(TTP) 스코프 확장 — 안 함(등록 시 계정 lock되어 어차피 skip)
- 플렉스·기타 큐 알림 — 안 함(솔로 랭크만)
- 화이트리스트 테이블 / 시즌 게이트 완화 — 안 함

## 핵심 설계

**마커**: 신규 상태 필드 없이, `enabled + primary + platform=KR` 인 `PlayerRiotAccount` 존재를
"솔랭 추적 대상" 신호로 재사용한다. 계정이 붙는 순간 솔랭 전용 선수로 취급된다.

### ① 솔랭 전용 선수 등록 (신규 admin 엔드포인트)

`POST /api/admin/players/solo-rank`
```json
{ "name": "Deft", "imageUrl": null, "riotId": "Deft#8366" }
```
처리 (신규 `PlayerAdminService.createSoloRankPlayer` — 기존 `update`와 별도):
1. 동명 선수 존재 시 400 (중복 방지)
2. `Player.builder().name(name).imageUrl(imageUrl).build()` 저장
3. `player.overrideGameAccounts("[{\"region\":\"KR\",\"riotId\":\"<riotId>\",\"tier\":null}]")`
   - `overrideGameAccounts`가 `gameAccountsLocked=true`까지 설정 → 크롤러 덮어쓰기 차단
4. `playerRiotAccountSyncService.syncPlayerAccountNow(player)`
   - Riot API로 puuid 해석 후 `PlayerRiotAccount(enabled, primary, KR)` 생성
   - Riot 404(미존재) → 예외 전파 → 트랜잭션 롤백 → 400
   - Riot 장애 → 502

**LCK 출전 체크를 우회**한다(전용 메서드라 `hasLeagueParticipation` 미호출). 반복 사용 가능 —
향후 다른 은퇴 선수도 같은 API로 추가.

riotId 검증은 기존 `serializeGameAccounts` 규칙 재사용: `region` non-empty, `riotId` = `^.+#.+$`.

### ② Riot 계정 sync 스코프 확장

`PlayerRiotAccountSyncService.syncPrimaryAccounts` (line 42) 가 쓰는 대상 조회를
신규 쿼리로 교체:

```
findSoloRankSyncTargets(league) = (league 출전 기록 보유 선수) ∪ (PlayerRiotAccount 보유 선수)
```

→ 시드된 솔랭 전용 선수의 puuid가 매일 06:00 sync로 자동 갱신·유지된다.
`PlayerService`(크롤러)의 두 `findPlayersByLeagueName("LCK")` 호출은 **그대로 둔다**(계정 lock으로 skip).

### ③ 구독 자격 확장 (Approach A)

자격 규칙:
```
구독가능(player) = LCK 2026 출전  OR  enabled+primary+KR PlayerRiotAccount 보유
```

`MobilePlayerSubscriptionService` + `PlayerRepository` 3개 지점:

- **`getAvailablePlayers`** (검색 목록, 페이지네이션 native 쿼리):
  기존 LCK 2026 랭크 서브쿼리에 `UNION ALL` 브랜치 추가 —
  enabled+primary+KR 계정 보유 & LCK 2026 미출전 선수를 `players`에서 투영.
  팀 필드(teamId/teamCode/teamName/teamImageUrl)는 `NULL`.
  페이지네이션·카운트는 SQL에서 그대로 처리(카운트 쿼리도 동일 UNION).
  `teamId` 필터 지정 시 팀 없는 솔랭 전용 선수는 자연히 제외됨.

- **`subscribe`**: `requireLckPlayer` 실패 시 계정 기준 옵션 재조회. LCK·계정 둘 다 없을 때만
  400. 성공 시 `MemberFavoritePlayer` 저장(기존과 동일).

- **`getSubscriptions`**: 기존 `findLckPlayerOptionsByPlayerIds` 결과에 계정 기준 옵션을 병합해
  솔랭 전용 구독 선수도 목록에 표시(현재는 `option == null`로 걸러짐).

DTO(`PlayerSubscriptionResponse`)는 이미 팀 필드 nullable → 변경 불필요.

### 데이터 흐름 요약

```
[admin] POST /solo-rank {name, riotId}
   → Player 생성 + gameAccounts(lock) + syncPlayerAccountNow → PlayerRiotAccount(puuid)
        ↓ (이후)
매일 06:00 findSoloRankSyncTargets → puuid 유지
10초 폴링 findTrackedAccountsByPlatform → 솔랭 감지 → push
   ├ all_solo_rank 토픽 (구독 무관)
   └ MemberFavoritePlayer 구독자 (② 자격 확장으로 구독 가능)
```

## 컴포넌트별 책임

| 유닛 | 책임 | 의존 |
|---|---|---|
| `PlayerAdminService.createSoloRankPlayer` | 솔랭 전용 선수 생성 + 계정 해석 | PlayerRepository, PlayerRiotAccountSyncService |
| `BackofficeController` (신규 POST) | 요청 검증·매핑, Riot 오류→HTTP 상태 | PlayerAdminService |
| `PlayerRepository.findSoloRankSyncTargets` | sync 대상 = 리그출전 ∪ 계정보유 | — |
| `PlayerRepository` 구독 쿼리 3종 | 계정 기준 자격을 UNION/보조조회로 포함 | — |
| `MobilePlayerSubscriptionService` | 구독 목록·추가·표시 시 계정 기준 병합 | PlayerRepository |

## 오류 처리

- 등록 API: 중복 이름 400 / riotId 형식 오류 400 / Riot 404 → 롤백 400 / Riot 장애 502
  (기존 백오피스 `update`의 매핑 재사용)
- 구독 추가: LCK·계정 둘 다 미해당 → 기존 문구 유지("현재 LCK 소속 선수가 아닙니다") 또는
  "구독 가능한 선수가 아닙니다"로 일반화(구현 시 확정)

## 테스트

- `MobilePlayerSubscriptionServiceTest`
  - 솔랭 전용 선수(계정 보유, 2026 미출전)가 `getAvailablePlayers`에 노출
  - 해당 선수 `subscribe` 성공, `getSubscriptions`에 표시
  - LCK·계정 둘 다 없는 선수 `subscribe` 400
- `PlayerAdminService` (또는 컨트롤러 테스트)
  - 생성 성공 시 Player + PlayerRiotAccount 저장
  - 중복 이름 400
  - Riot 404 시 롤백(Player 미저장)
- `findSoloRankSyncTargets`: 리그 출전자 + 계정만 보유자 모두 반환(리포지토리 통합 테스트)

## 실행 데이터 작업 (배포 후)

1. `POST /api/admin/players/solo-rank {name:"Deft", riotId:"Deft#8366"}`
2. `POST /api/admin/players/solo-rank {name:"Rascal", riotId:"Rascal#1231"}`
3. Peanut: 조치 불필요(감시 중). 2026 LCK 미출전이면 ③ 자격 확장으로 자동 구독 가능.
