---
type: Playbook
title: 마이그레이션 추가
description: 운영 baseline V30을 고려한 멱등 Flyway 마이그레이션 작성·검증 절차.
tags: [playbook, how-to, flyway, migration, database]
timestamp: 2026-06-21T00:00:00Z
---

# 목적

운영 [baseline V30](/operations/db-baseline.md) 환경에서 안전한(멱등) Flyway 마이그레이션을 추가하는 절차.

# 단계

1. **번호 정하기** — `src/main/resources/db/migration/`의 최신 `VNN` 다음 번호. 한 번에 하나씩 머지하면 충돌이 없다. → [브랜치 워크플로](/operations/branch-workflow.md)
2. **파일 작성** — `VNN__description.sql`.
3. **멱등하게** — 기존 객체를 변경/삭제할 땐 `information_schema` 존재 검사 + `PREPARE/EXECUTE` 패턴. 운영에 V1~V30 객체가 없을 수 있음을 가정. (V41이 참고 사례)
4. **테스트 픽스처 갱신** — 새 마이그레이션이 pre-V31 컬럼을 참조하면 `src/test/resources/db/pre_v31_schema.sql`에도 그 컬럼이 있는지 확인.
5. **검증** — MySQL Testcontainers 스키마 테스트가 [PR CI](/operations/ci-cd.md)에서 돈다. 로컬에서 막히면 MySQL 컨테이너에 픽스처+마이그레이션을 직접 적용해 SQL을 검증할 수 있다.

# 멱등 패턴 예시

```sql
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'league_match'
       AND INDEX_NAME = 'idx_example') = 0,
    'CREATE INDEX idx_example ON league_match (col)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

# 체크리스트

- [ ] 번호가 최신 다음이고 다른 미머지 브랜치와 안 겹침
- [ ] 기존 객체 변경/삭제가 멱등 (존재 검사)
- [ ] 필요한 pre-V31 컬럼이 테스트 픽스처에 있음
- [ ] PR CI(`build-and-test`) 통과

# Citations

[1] [운영 DB baseline V30](/operations/db-baseline.md)
[2] [CLAUDE.md — Flyway migrations](/references/claude-md.md)
