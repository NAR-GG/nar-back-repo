---
type: Architecture Pattern
title: 영속성
description: JPA/Hibernate(MySQL) + Flyway 마이그레이션 + Elasticsearch 검색의 데이터 계층.
tags: [architecture, jpa, hibernate, flyway, elasticsearch, mysql]
timestamp: 2026-06-21T00:00:00Z
---

# JPA / Hibernate

- MySQL8Dialect 사용.
- **`open-in-view: false`** — 뷰 렌더링 중 지연 로딩이 막힌다. 필요한 연관은 **명시적 fetch**(fetch join / `@EntityGraph`)로 가져온다.
- N+1과 과도한 데이터 전송은 `@Query` + DTO projection(`select new ...`)으로 회피한다. → [컨벤션](/architecture/conventions.md)
- 복잡 쿼리는 `*RepositoryCustom` + `*RepositoryImpl` 패턴.

# Flyway

- 마이그레이션은 `src/main/resources/db/migration/VNN__description.sql`.
- 운영 DB는 **baseline V30**으로 시작 — V1~V30이 실제 실행된 적이 없어 dev와 스키마가 다를 수 있다. 모든 마이그레이션은 **멱등**해야 한다. → [DB 베이스라인](/operations/db-baseline.md)
- 기동 시 `FlywayConfig`가 `repair()` → `migrate()` 전략을 적용한다.

# Elasticsearch

- 검색 문서는 별도 리포지토리 설정으로 관리한다. → [search 모듈](/modules/search.md)

# 마이그레이션 추가

새 마이그레이션 작성 절차(번호·멱등 패턴·검증)는 [마이그레이션 추가 플레이북](/playbooks/add-migration.md) 참조.

# Citations

[1] [CLAUDE.md — Data Persistence](/references/claude-md.md)
[2] [운영 DB baseline V30](/operations/db-baseline.md)
