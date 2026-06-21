---
type: Module
title: monitor
description: Riot API 폴링 기반 실시간 라이브 경기 모니터링.
tags: [module, monitor, live, riot, polling, stub]
timestamp: 2026-06-21T00:00:00Z
---

# 상태

🟡 Stub — 추후 상세 보강.

# 역할

Riot API를 폴링해 라이브 경기를 실시간 모니터링한다. 폴 간격은 설정 가능(기본 10초). → [스케줄링](/architecture/scheduling.md)

# 코드 위치

`app/monitor/`

# 기능 플래그

`RIOT_MONITOR_ENABLED`, `RIOT_API_ENABLED`로 켜고 끈다. → [환경변수](/operations/environment.md)

# 관련

- Riot API 연동 기반은 [riot](/modules/riot.md).

# Citations

[1] [CLAUDE.md — Key Modules: monitor](/references/claude-md.md)
