---
type: Architecture Pattern
title: 캐싱
description: Caffeine 기반 2-tier 캐시. 휘발성 데이터는 TTL, 불변 데이터는 LRU 무만료.
tags: [architecture, cache, caffeine, performance]
timestamp: 2026-06-21T00:00:00Z
---

# 원칙

`config/CacheConfig.java`에 두 종류의 캐시 티어를 정의한다. 데이터의 휘발성에 따라 티어를 고른다.

# 티어

| 티어 | 캐시 | 정책 |
|------|------|------|
| **휘발성** | `todaySchedules`, `todayMatchDetails` | TTL 1시간 |
| **불변** | `dailySchedules`, `matchDetails`, `gameRecords` | LRU, 만료 없음 (한 번 기록되면 안 바뀜) |

# 가이드

- 자주 바뀌는 데이터(오늘 일정 등)는 TTL 티어에.
- 한 번 확정되면 안 바뀌는 과거 기록은 LRU 티어에 둬 재계산을 피한다.
- 캐시 무효화는 `schedule` 모듈의 `CacheEvictionService` 등에서 명시적으로 처리한다. → [schedule 모듈](/modules/schedule.md)

# Citations

[1] [CLAUDE.md — Caching (Caffeine)](/references/claude-md.md)
