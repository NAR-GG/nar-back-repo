---
type: Operations
title: 운영 DB 베이스라인 (V30)
description: 운영 DB는 Flyway baseline V30. V1~V30 객체 존재를 가정하지 말 것. 마이그레이션은 멱등 필수.
tags: [operations, database, flyway, migration, incident]
timestamp: 2026-06-21T00:00:00Z
---

# 핵심

운영 DB는 `baseline-version: 30`으로 Flyway가 시작돼, **V1~V30 마이그레이션이 실제로 실행된 적이 없다.** 따라서 dev 스키마에 존재하는 객체(예: V6의 `idx_league_match_name_date` 인덱스)가 운영에는 없을 수 있다.

# 왜 (운영 장애 교훈)

2026-06-11, V41이 `DROP INDEX idx_league_match_name_date`를 **무조건** 실행했다가 운영에서 실패했다. MySQL DDL은 롤백 불가라 Flyway 실패 기록이 남아 앱 기동 불가 → 운영 502 장애. 이후 V41은 `information_schema` 존재 검사 + `PREPARE/EXECUTE`로 멱등화됐다.

# 규칙

- 기존 객체를 변경/삭제할 때는 반드시 **`information_schema` 존재 검사 + `PREPARE/EXECUTE`** 패턴으로 멱등하게 작성한다.
- 운영 스키마를 dev 기준으로 가정하지 않는다.
- `FlywayConfig`의 기동 시 `repair()` → `migrate()`가 실패 기록을 정리하지만, **스크립트가 멱등해야** 재실행이 안전하다.

# 테스트 픽스처

`src/test/resources/db/pre_v31_schema.sql`가 "운영 V30 베이스라인" 스냅샷이다. V31+ 마이그레이션이 참조하는 pre-V31 컬럼은 이 픽스처에도 있어야 한다. (2026-06-21: `league_match`에 `league_name`이 빠져 V41 인덱스 테스트가 깨져 보강함.) 이 MySQL Testcontainers 스키마 테스트는 이제 [PR CI](/operations/ci-cd.md)가 머지 전 실행한다.

# 마이그레이션 추가 절차

[마이그레이션 추가 플레이북](/playbooks/add-migration.md) 참조. 영속성 전반은 [영속성](/architecture/persistence.md).

# Citations

[1] [CLAUDE.md — Flyway / migrations](/references/claude-md.md)
[2] 2026-06-11 V41 운영 장애 (502), 2026-06-21 픽스처 보강
