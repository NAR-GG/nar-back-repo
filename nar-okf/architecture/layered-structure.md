---
type: Architecture Pattern
title: 레이어 구조
description: api / app / domain / config / common 5개 레이어로 나뉜 백엔드 패키지 구조.
tags: [architecture, layers, package-structure]
timestamp: 2026-06-21T00:00:00Z
---

# 원칙

코드는 `com.toy.nar` 아래 5개 레이어로 나뉜다. 의존 방향은 위에서 아래로 흐른다 (api → app → domain).

# 레이어

| 레이어 | 위치 | 역할 |
|--------|------|------|
| **api** | `api/` | REST 컨트롤러. `v1`(레거시), `v3`(현행), `admin`, `kakao`, `mobile` |
| **app** | `app/` | 비즈니스 로직, 외부 API 클라이언트, DTO. 도메인 모듈이 여기 모임 → [Modules](/modules/) |
| **domain** | `domain/` | JPA 엔티티 + 리포지토리 |
| **config** | `config/` | Spring 빈 설정 (캐시·스케줄러·시큐리티·WebClient 등) |
| **common** | `common/` | 에러 처리, 필터, 유틸리티 |

# API 버전

- `api/v3/` 가 **현행** 컨트롤러. 신규 엔드포인트는 v3에 추가한다.
- `api/v1/` 은 레거시 — 신규 작업 금지.
- `api/mobile/` 는 [`warding`](/overview.md) 모바일 앱 전용 엔드포인트.

# 복잡 쿼리

복잡한 쿼리는 `*RepositoryCustom` + `*RepositoryImpl` 패턴으로 분리한다. → [영속성](/architecture/persistence.md), [컨벤션](/architecture/conventions.md)

# 새 기능 추가

레이어를 가로지르는 새 기능 추가 절차는 [새 기능 추가 플레이북](/playbooks/add-feature.md) 참조.

# Citations

[1] [CLAUDE.md — Architecture / Layer Structure](/references/claude-md.md)
