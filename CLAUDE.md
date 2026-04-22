# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 언어 규칙

문서, 계획, 주석 등 모든 서술형 산출물은 **한국어**로 작성한다.

## Project Overview

**NAR.GG** — A League of Legends esports analytics service (Spring Boot 3.5.3, Java 17, MySQL, Elasticsearch). Tracks champion combinations, matchup stats, match schedules, and team performance metrics.

## Common Commands

```bash
# Local dev — start MySQL first
docker-compose up -d              # MySQL on port 3308 (nar_id / nar_pw)

# Build & run
./gradlew clean build -x test     # Build without tests
./gradlew bootRun                 # Run app (uses application-prod.yml)
./gradlew test                    # Run all tests
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
GOOGLE_DRIVE_CSV_ID
RIOT_MONITOR_ENABLED, RIOT_API_ENABLED   # feature flags for scheduling
```

## CI/CD

GitHub Actions (`.github/workflows/deploy.yml`): Gradle build → Docker image → push to Docker Hub → SSH deploy to EC2 → health check against `/v3/api-docs`. Production logs viewable via Dozzle at `https://api.nar.kr/dozzle`.
