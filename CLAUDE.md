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

## Project Overview

**NAR.GG** — A League of Legends esports analytics service (Spring Boot 3.5.3, Java 17, MySQL, Elasticsearch). Tracks champion combinations, matchup stats, match schedules, and team performance metrics.

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
| `search/` | Elasticsearch indexing and fulltext search |
| `youtube/` | YouTube broadcast discovery and metadata sync |
| `riot/` | Riot API integration (player ranks, accounts) |
| `participant/` | Player, team, champion management |

### Data Persistence
- **JPA + Hibernate** with MySQL8Dialect; `open-in-view: false` (explicit fetch required)
- **Flyway** migrations in `src/main/resources/db/migration/` (V1–V31+, baseline at V30)
- **Elasticsearch** via separate repository config for search documents
- Complex queries use `*RepositoryCustom` + `*RepositoryImpl` pattern

### Caching (Caffeine)
Two tiers defined in `CacheConfig.java`:
- **TTL 1 hour**: `todaySchedules`, `todayMatchDetails` (volatile)
- **LRU, no expiry**: `dailySchedules`, `matchDetails`, `gameRecords` (immutable once recorded)

### Scheduling
5-thread pool (`SchedulerConfig.java`). Key tasks:
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
ELASTICSEARCH_URI
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
