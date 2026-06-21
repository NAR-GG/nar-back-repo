---
type: Module
title: lolesports
description: LoL Esports API 동기화 — 일정·경기·순위(standings)를 가져와 저장한다.
tags: [module, lolesports, sync, schedule, stub]
timestamp: 2026-06-21T00:00:00Z
---

# 상태

🟡 Stub — 추후 상세 보강.

# 역할

LoL Esports API에서 일정·경기 결과·순위를 동기화한다. 정기 동기화는 30분마다 돈다. → [스케줄링](/architecture/scheduling.md)

# 코드 위치

`app/lolesports/` (라이브 수집·reconciliation 포함)

# 관련

- 가져온 경기는 [schedule](/modules/schedule.md)·[analysis](/modules/analysis.md)에서 활용.
- 라이브 경기 실시간 추적은 [monitor](/modules/monitor.md).

# Citations

[1] [CLAUDE.md — Key Modules: lolesports](/references/claude-md.md)
