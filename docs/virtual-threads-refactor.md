# Virtual Threads Refactor Notes

## Current async/non-blocking usage

- `RiotApiClient`, `YoutubeService`, and `ChampionDataService` use `WebClient`, but their public APIs call `.block()`. In the current Spring MVC + JPA/JDBC application, these paths are synchronous from the caller's point of view.
- `WorldsService#getWorldsMatches` previously used Reactor as an internal fan-out pipeline with `Flux.flatMap(..., 5)` and then blocked at the MVC boundary.
- `TeamDashboardService#getTeamDashboard` uses `CompletableFuture` to scatter independent blocking DB aggregations and gather the result into one MVC response.

## Fit for this project

This service is primarily blocking MVC: servlet request handling, Spring Data JPA, jOOQ/JDBC, scheduled sync jobs, and external HTTP APIs. Virtual threads match that shape better than pushing WebFlux through the service layer, because the code can stay imperative while blocking JDBC/HTTP waits stop consuming scarce platform threads.

The important constraint is downstream capacity. Virtual threads make waiting cheap, but they do not increase MySQL, Elasticsearch, Riot, YouTube, or LoL Esports API capacity. Concurrency still needs limits around fan-out work.

## Refactor applied

- Java toolchain moved to 21 because virtual threads require JDK 21.
- `spring.threads.virtual.enabled=true` enables Spring Boot virtual-thread integration for supported web/task execution paths.
- `applicationTaskExecutor` now uses virtual threads with a concurrency limit. `TeamDashboardService` continues to parallelize independent DB reads, but no longer depends on a fixed platform thread pool.
- Scheduled tasks now use `SimpleAsyncTaskScheduler` with virtual threads and the existing effective concurrency limit of 5.
- `WorldsService#getWorldsMatches` now uses `CompletableFuture` on the virtual-thread `applicationTaskExecutor` plus a local `Semaphore(5)`. This preserves the external LoL Esports API concurrency cap without keeping a Reactor pipeline that is immediately blocked.

## What remains intentionally unchanged

- WebClient callers that immediately `.block()` can be migrated to `RestClient` later if the goal is to remove WebFlux dependencies. That is a cleanup choice, not a prerequisite for virtual-thread benefits.
