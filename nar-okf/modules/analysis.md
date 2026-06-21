---
type: Module
title: analysis
description: 팀/선수 분석 쿼리 — 챔피언 조합·매치업 통계·팀 성적 지표.
tags: [module, analysis, stats, stub]
timestamp: 2026-06-21T00:00:00Z
---

# 상태

🟡 Stub — 추후 상세 보강.

# 역할

팀·선수 분석 쿼리를 제공한다. 챔피언 조합, 매치업 통계, 팀 성적 지표 등. 복잡 쿼리는 `*RepositoryImpl` + DTO projection으로 작성된다. → [영속성](/architecture/persistence.md), [컨벤션](/architecture/conventions.md)

# 코드 위치

`app/analysis/`

# 관련

- 원천 데이터는 [lolesports](/modules/lolesports.md) 동기화 결과.

# Citations

[1] [CLAUDE.md — Key Modules: analysis](/references/claude-md.md)
