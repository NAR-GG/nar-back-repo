# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 언어 규칙

문서, 계획, 주석 등 모든 서술형 산출물은 **한국어**로 작성한다.

## 브랜치 & 작업 규칙

트렁크 기반 개발(Trunk-Based Development)을 따른다. `main`이 유일한 트렁크이며 항상 배포 가능한 상태를 유지한다.

- **`main` 브랜치는 절대 직접 수정하지 않는다.** PR을 통해서만 업데이트된다.
- **모든 개발 작업은 `main` 브랜치 기준으로 한다.** 새 기능/수정은 `main`에서 단기 브랜치(`feat/*`, `fix/*` 등)를 따고, `main`을 타깃으로 PR을 올린다.
- **브랜치는 짧게 유지한다.** 가능하면 하루~이틀 안에 머지하고, 장수 `develop` 류 통합 브랜치는 두지 않는다.
- **미완성 기능은 feature flag로 OFF 상태로 머지한다** (예: `RIOT_MONITOR_ENABLED`, `RIOT_API_ENABLED` 패턴). 트렁크가 항상 배포 가능해야 하므로 미완성 코드를 장수 브랜치에 쌓지 않는다.
- **메인 레포 디렉토리(`/Users/changha/Documents/25-3-quarter/nar`)는 직접 수정하지 않는다.** 작업은 워크트리에서 진행한다.
- Claude가 작업할 때는 항상 워크트리를 사용하고, 완료 후 `main`으로 PR을 올린다.

## 서비스 가용성 수칙 — 경기 시간 보호 (필수)

**인프라·배포 작업 전에 반드시 오늘 경기 일정부터 확인한다:**

```bash
curl -s "https://api.nar.kr/api/schedule?date=$(date +%F)" | python3 -c "
import sys,json
for m in json.load(sys.stdin)['matches']:
    print(m['scheduledTime'], m['leagueInfo'], m['matchTitle'], m['matchStatus'])"
```

- **경기 창(시작 1시간 전 ~ 마지막 세트 종료)과 겹치는 인프라 작업 금지.** `inProgress`가 하나라도 있으면 즉시 중단.
- 여기서 인프라 작업이란: **스케줄러·웹 파드 재시작/배포, main 머지(자동 배포 트리거), DB·시크릿 변경, 서버(맥미니·춘천 박스) 재부팅, 의존 인프라(MySQL·Tailscale·cloudflared) 조작** 전부.
- 이유: 라이브폴링의 세트 시작 감지(edge)가 **인메모리**(`LivePollingScheduler.startNotifiedGameIds`)라 재시작하면 유실된다. 재기동한 파드는 진행 중 세트를 "이미 시작됨"으로 보고 넘어가므로 **그 세트의 라이브위젯(push-to-start)·시작 알림이 통째로 누락**되고 복구 방법이 없다. 실사고: 2026-08-22 DK vs GEN — ES 박스 재부팅 → 파드 CrashLoop 44분 → 1세트 위젯 미발송.
- **실서비스 기능에 영향을 줄 수 있는 작업은 사용자 승인을 먼저 받는다.** 자율 진행 금지. 진행 중에는 단계마다 그때그때 브리핑한다 (무엇을 건드리는지, 서비스에 어떤 영향인지, 언제 끝나는지).
- 경기 중 작업이 불가피하면: 사용자 승인 + 세트 사이 휴식 창을 노리고, 재시작 후 라이브폴링 재개 로그(`[live-discovery]`)까지 확인하고 끝낸다.

## Project Overview

**NAR.GG** — A League of Legends esports analytics service (Spring Boot 3.5.3, Java 17, MySQL). Tracks champion combinations, matchup stats, match schedules, and team performance metrics.

## Common Commands

```bash
# Local dev — start MySQL first
docker-compose up -d              # MySQL on port 3308 (nar_id / nar_pw)

# Build & run
./gradlew clean build -x test     # Build without tests
./gradlew bootRun                 # Run app locally (defaults to application-dev.yml)
./gradlew test                    # Run all tests

# prod 프로파일로 실행해야 할 때만 명시
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

### Run a single test
```bash
./gradlew test --tests "com.toy.nar.app.schedule.ScheduleServiceTest"
```

### API docs (after running)
- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/v3/api-docs`

## Architecture

### Layer Structure
```
api/          REST controllers (v1 legacy, v3 current, admin, kakao)
app/          Business logic, external API clients, DTOs
domain/       JPA entities + repositories
config/       Spring beans (cache, scheduler, web client, etc.)
common/       Error handling, filters, utilities
```

### Key Modules under `app/`
| Module | Purpose |
|--------|---------|
| `lolesports/` | LoL Esports API sync (schedules, matches, standings) |
| `analysis/` | Team/player analytics queries |
| `monitor/` | Real-time live game monitoring via Riot API polling |
| `schedule/` | Match schedule management and notifications |
| `search/` | 자동완성 검색 (MySQL 코드·이름 매칭) |
| `youtube/` | YouTube broadcast discovery and metadata sync |
| `riot/` | Riot API integration (player ranks, accounts) |
| `participant/` | Player, team, champion management |

### Data Persistence
- **JPA + Hibernate** with MySQL8Dialect; `open-in-view: false` (explicit fetch required)
- **Flyway** migrations in `src/main/resources/db/migration/` (V1–V31+, baseline at V30)
- Complex queries use `*RepositoryCustom` + `*RepositoryImpl` pattern

### Caching (Caffeine)
`CacheConfig.java`에 정의. **웹과 스케줄러가 별도 파드라 캐시도 두 벌이다.**
Caffeine은 JVM 안에 있어서 `CacheEvictionService`의 evict는 자기 JVM만 지운다.
그래서 **evict 대상 캐시(`todaySchedules`, `todayMatchDetails`, `dailySchedules`, `matchDetails`)는
반드시 TTL이 있어야 한다** — 없으면 웹 파드가 재시작할 때까지 낡은 값을 준다.
`CacheConfigTest`가 이 불변조건을 잠근다.

- **TTL 60초**: `todaySchedules`, `todayMatchDetails` (진행 중 경기 스코어가 실린다)
- **TTL 10분**: `dailySchedules`, `matchDetails` (과거 데이터. CSV 인제스트가 나중에 채운다)
- **TTL 30초~1시간**: `mobileScheduleCalendar`, `mobileScheduleFilters`, `adminStats*`
- **만료 없음(LRU)**: `gameRecords` (적재되면 안 바뀐다. evict 대상도 아니다)

라이브 경로(`/api/mobile/live/games/*`)는 캐시를 타지 않는다. 실시간 스코어는 캐시와 무관하다.

### Scheduling
**스케줄러는 웹과 다른 파드에서 돈다** (`infra/k8s/nar-scheduler.yaml`, 같은 이미지 + `APP_SCHEDULING_ENABLED=true`).
`nar-web`은 `false`다. ShedLock 같은 중복 실행 가드가 없어서 **스케줄러 파드는 언제나 정확히 하나**여야 한다
(그래서 `strategy: Recreate`, `replicas: 1`). 두 벌 돌면 라이브 폴링·푸시가 이중으로 나간다.

가상 스레드 기반, 동시 실행 5개 제한(`SchedulerConfig.java`). 잡이 늘어 밀리면 파드를 늘리는 게 아니라
이 한도를 올리거나 도메인별로 Deployment를 쪼갠다(각각 `replicas: 1`).

Key tasks:
- LoL Esports sync: every 30 mins
- Team metadata sync: daily 4:15 AM
- Discord daily summary: daily 9 AM
- Riot live monitor: configurable poll interval (default 10s)

### External APIs
LoL Esports API, Riot API, YouTube Data API, Google Drive (CSV import), Discord Webhooks. All credentials injected via environment variables — never hardcoded.

## Adding a New Feature

1. Entity in `domain/{domain}/entity/`
2. Repository in `domain/{domain}/repository/` (extend `JpaRepository`; complex queries go in `*RepositoryImpl`)
3. Service in `app/{feature}/service/`
4. DTOs in `app/{feature}/dto/`
5. Controller in `api/v3/`
6. Flyway migration `src/main/resources/db/migration/VNN__description.sql`

## Key Conventions

- Constructor injection via Lombok `@RequiredArgsConstructor`
- DTOs use `@Data` + `@Builder`
- Custom exceptions extend `CustomException` with `ErrorCode` enums; handled globally by `GlobalExceptionHandler`
- Use `@Query` with DTO projection (`select new ...`) to avoid N+1 and reduce data transfer
- Batch inserts use JDBC batch size 50

## Environment Variables

```
DB_URL, DB_USERNAME, DB_PASSWORD
YOUTUBE_API_KEY
LOL_ESPORTS_KEY
RIOT_API_KEY
DISCORD_WEBHOOK_URL, DISCORD_PLAYER_WEBHOOK_URL
DISCORD_ROSTER_WEBHOOK_URL                # LCK 로스터 변동 알림. 비우면 DISCORD_WEBHOOK_URL로 폴백
GOOGLE_DRIVE_CSV_ID
RIOT_MONITOR_ENABLED, RIOT_API_ENABLED   # feature flags for scheduling
JWT_SECRET

# iOS Live Activity 서버 푸시 (APNs 직결). 5개가 모두 채워져야 발송한다 — 하나라도 비면 전 구간 skip.
# FCM 으로는 대체 불가: ActivityKit 푸시 토큰은 FCM 등록 토큰이 아니다.
APNS_ENABLED                             # 기본 false
APNS_KEY_PATH                            # Apple Developer 에서 받은 .p8 파일 경로
APNS_KEY_ID, APNS_TEAM_ID, APNS_BUNDLE_ID
APNS_HOST                                # 기본 https://api.push.apple.com (개발 빌드는 api.sandbox.push.apple.com)
APNS_PUSH_TO_START_ENABLED               # 서버가 잠금화면 카드를 생성(push-to-start, iOS 17.2+). 기본 false
```

### 스케줄러 전역 스위치

`app.scheduling.enabled` (env `APP_SCHEDULING_ENABLED`)가 모든 `@Scheduled` 등록을 좌우한다(`SchedulerConfig`).
**prod 프로파일만 ON**이고, 로컬(`dev`)은 값이 없으므로 OFF다. 로컬 DB가 prod 데이터 사본이고
YouTube/Riot API 키, Discord 웹훅, FCM 자격증명을 prod와 공유하기 때문에 로컬 폴링이 prod의
API 쿼터를 태우거나 prod 채널·실유저에게 알림을 보낼 수 있다.

로컬에서 스케줄 로직을 확인해야 하면 개별 잡은 백오피스 수동 트리거 API를 쓰고,
스케줄러 자체를 검증할 때만 `app.scheduling.enabled=true`로 켠다(공유 자원 영향 확인 후).

## CI/CD

GitHub Actions (`.github/workflows/deploy.yml`): Gradle build → Docker image → push to Docker Hub → SSH deploy to EC2 → health check against `/v3/api-docs`. Production logs viewable via Dozzle at `https://api.nar.kr/dozzle`.

## Agent skills

### Issue tracker

이슈는 GitHub Issues(`NAR-GG/nar-back-repo`)에 있고 `gh` CLI로 다룬다. `docs/agents/issue-tracker.md` 참고.

### Triage labels

기본 라벨 어휘 5종(`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`)을 그대로 쓴다. `docs/agents/triage-labels.md` 참고.

### Domain docs

single-context 레이아웃(루트 `CONTEXT.md` + `docs/adr/`). 아직 둘 다 없으며, 필요해질 때 `/domain-modeling`이 만든다. `docs/agents/domain.md` 참고.
