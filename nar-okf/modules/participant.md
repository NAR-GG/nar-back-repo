---
type: Module
title: participant
description: 선수·팀·챔피언 등 참가자 메타데이터 관리.
tags: [module, participant, player, team, champion, stub]
timestamp: 2026-06-21T00:00:00Z
---

# 상태

🟡 Stub — 추후 상세 보강.

# 역할

선수·팀·챔피언 메타데이터를 관리한다. 팀 메타데이터 동기화는 매일 04:15에 돈다. → [스케줄링](/architecture/scheduling.md)

# 코드 위치

`app/participant/` (예: `PlayerRepository`의 LCK 선수 옵션 조회 등)

# 관련

- 분석·일정에서 이 메타데이터를 참조. → [analysis](/modules/analysis.md), [schedule](/modules/schedule.md)

# Citations

[1] [CLAUDE.md — Key Modules: participant](/references/claude-md.md)
