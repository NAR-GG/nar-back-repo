---
type: Architecture Pattern
title: 스케줄링
description: 5-thread 풀 기반 정기 작업. LoL Esports 동기화, 메타데이터 sync, Discord 요약, Riot 라이브 모니터.
tags: [architecture, scheduling, cron, background-jobs]
timestamp: 2026-06-21T00:00:00Z
---

# 원칙

`config/SchedulerConfig.java`가 5-thread 풀을 구성한다. 정기 작업은 이 풀에서 돈다.

# 주요 작업

| 작업 | 주기 | 모듈 |
|------|------|------|
| LoL Esports 동기화 (일정·경기·순위) | 30분마다 | [lolesports](/modules/lolesports.md) |
| 팀 메타데이터 동기화 | 매일 04:15 | [participant](/modules/participant.md) |
| Discord 일일 요약 | 매일 09:00 | [schedule](/modules/schedule.md) |
| Riot 라이브 모니터 | 폴 간격 설정 가능 (기본 10초) | [monitor](/modules/monitor.md) |

# 기능 플래그

스케줄 작업은 환경변수로 켜고 끈다 — `RIOT_MONITOR_ENABLED`, `RIOT_API_ENABLED` 등. 미완성·비용 민감 작업은 플래그로 OFF 상태로 둔다. → [환경변수](/operations/environment.md), [브랜치 워크플로](/operations/branch-workflow.md)

# Citations

[1] [CLAUDE.md — Scheduling](/references/claude-md.md)
