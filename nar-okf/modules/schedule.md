---
type: Module
title: schedule
description: 경기 일정 관리와 알림. 캐시 무효화·Discord 일일 요약 포함.
tags: [module, schedule, notification, cache, stub]
timestamp: 2026-06-21T00:00:00Z
---

# 상태

🟡 Stub — 추후 상세 보강.

# 역할

경기 일정을 관리하고 알림을 보낸다. Discord 일일 요약(매일 09:00)과 캐시 무효화(`CacheEvictionService`)를 담당한다. → [스케줄링](/architecture/scheduling.md), [캐싱](/architecture/caching.md)

# 코드 위치

`app/schedule/`

# 관련

- 일정 데이터는 [lolesports](/modules/lolesports.md) 동기화 결과.

# Citations

[1] [CLAUDE.md — Key Modules: schedule](/references/claude-md.md)
