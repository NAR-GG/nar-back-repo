---
type: Reference
title: CLAUDE.md (개발 가이드)
description: 저장소 루트의 개발 가이드 원본. 아키텍처·규칙·진행 상황의 1차 출처.
resource: https://github.com/NAR-GG/nar-back-repo/blob/main/CLAUDE.md
tags: [reference, guide, source-of-truth]
timestamp: 2026-06-21T00:00:00Z
---

# 개요

`CLAUDE.md`는 저장소 루트의 개발 가이드다. 이 번들의 아키텍처·운영·규칙 개념들은 모두 이 문서를 **1차 출처**로 한다.

# 다루는 내용

- 언어 규칙(한국어) / 브랜치 규칙 → [브랜치 워크플로](/operations/branch-workflow.md)
- 레이어 구조 → [레이어 구조](/architecture/layered-structure.md)
- 모듈 → [Modules](/modules/)
- 영속성(JPA·Flyway·ES) → [영속성](/architecture/persistence.md)
- 캐싱 → [캐싱](/architecture/caching.md)
- 스케줄링 → [스케줄링](/architecture/scheduling.md)
- 컨벤션 → [컨벤션](/architecture/conventions.md)
- 환경변수 → [환경변수](/operations/environment.md)
- CI/CD → [CI/CD](/operations/ci-cd.md)

# 주의

코드 구조·규칙이 바뀌면 **CLAUDE.md를 먼저 갱신**하고, 이 번들 개념들을 그에 맞춰 동기화한다. 번들은 CLAUDE.md를 대체하지 않고 탐색·연결을 보강한다. 진행 상황·TODO는 [GitHub 보드](/references/github-project.md)와 CLAUDE.md를 단일 출처로 한다.

# 자동 메모리와의 관계

이 레포에는 Claude Code 자동 메모리(`MEMORY.md` 등)도 있다. 메모리는 **에이전트 사적 메모(세션 간 피드백·선호)**, 번들은 **공유 가능한 지식 그래프**다. 역할이 다르므로 같은 사실을 중복 기록하지 않는다.

# Citations

[1] [CLAUDE.md (repo)](https://github.com/NAR-GG/nar-back-repo/blob/main/CLAUDE.md)
