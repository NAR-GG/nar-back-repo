---
type: Playbook
title: 새 기능 추가
description: 레이어 구조를 지키며 새 기능을 추가하는 절차 (엔티티→리포지토리→서비스→DTO→컨트롤러).
tags: [playbook, how-to, feature]
timestamp: 2026-06-21T00:00:00Z
---

# 목적

NAR.GG 백엔드에 새 기능을 일관되게 추가하는 절차. 레이어 구조는 [레이어 구조](/architecture/layered-structure.md) 참조.

# 단계

1. **엔티티** — `domain/{domain}/entity/` 에 JPA 엔티티 추가.
2. **리포지토리** — `domain/{domain}/repository/` 에 `JpaRepository` 상속. 복잡 쿼리는 `*RepositoryImpl`. → [영속성](/architecture/persistence.md)
3. **서비스** — `app/{feature}/service/` 에 비즈니스 로직. 생성자 주입(`@RequiredArgsConstructor`). → [컨벤션](/architecture/conventions.md)
4. **DTO** — `app/{feature}/dto/` 에 `@Data` + `@Builder`. 조회는 DTO projection으로.
5. **컨트롤러** — `api/v3/` 에 추가 (현행 버전). 모바일 전용이면 `api/mobile/`.
6. **마이그레이션** — 스키마 변경이 있으면 `src/main/resources/db/migration/VNN__description.sql`. → [마이그레이션 추가](/playbooks/add-migration.md)

# 작업 방식

- `main` 기준 단기 브랜치를 **워크트리**에서 따고, 완료 후 main PR. → [브랜치 워크플로](/operations/branch-workflow.md)
- 미완성 상태로 머지해야 하면 feature flag로 OFF. → [환경변수](/operations/environment.md)

# 체크리스트

- [ ] 생성자 주입 사용 (필드 주입 없음)
- [ ] 신규 엔드포인트는 `v3`(또는 `mobile`), `v1` 금지
- [ ] N+1 회피 (fetch join / DTO projection)
- [ ] 스키마 변경 시 멱등 마이그레이션 + CI 통과
- [ ] 서술형 산출물은 한국어

# Citations

[1] [CLAUDE.md — Adding a New Feature](/references/claude-md.md)
