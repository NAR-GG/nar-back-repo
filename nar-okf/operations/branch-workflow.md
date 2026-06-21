---
type: Operations
title: 브랜치 워크플로 (TBD)
description: 트렁크 기반 개발. main이 유일한 트렁크이며 항상 배포 가능. 단기 브랜치 → main PR.
tags: [operations, git, trunk-based-development, workflow, branch]
timestamp: 2026-06-21T00:00:00Z
---

# 원칙

트렁크 기반 개발(Trunk-Based Development)을 따른다. `main`이 **유일한 트렁크**이며 **항상 배포 가능한 상태**를 유지한다. (2026-06-21, git-flow의 장수 `v3-dev` 통합 브랜치를 폐기하고 전환함.)

# 규칙

- **`main`은 직접 수정 금지** — PR로만 갱신.
- 모든 작업은 `main` 기준 **단기 브랜치**(`feat/*`, `fix/*` 등)를 따서 진행하고, `main`을 타깃으로 PR.
- 브랜치는 짧게 유지(하루~이틀). 장수 develop 류 통합 브랜치를 두지 않는다.
- **미완성 기능은 feature flag로 OFF 상태로 머지**한다(예: `RIOT_MONITOR_ENABLED`). 미완성 코드를 장수 브랜치에 쌓지 않는다. → [환경변수](/operations/environment.md)
- 메인 레포 디렉토리는 직접 수정하지 않고 **워크트리**에서 작업한다.

# 머지 게이트

PR은 [CI(`build-and-test`)](/operations/ci-cd.md)를 통과해야만 머지된다. 브랜치 보호로 강제된다.

# Flyway 번호 주의

여러 미머지 브랜치가 각각 새 마이그레이션을 추가하면 번호가 충돌할 수 있다. 한 번에 하나씩 머지하면 자연히 안 겹친다. → [DB 베이스라인](/operations/db-baseline.md), [마이그레이션 추가](/playbooks/add-migration.md)

# Citations

[1] [CLAUDE.md — 브랜치 & 작업 규칙](/references/claude-md.md)
