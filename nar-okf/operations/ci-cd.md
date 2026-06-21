---
type: Operations
title: CI/CD
description: PR 검증 CI(build+test) + GitHub Actions 배포 + main 브랜치 보호.
resource: https://github.com/NAR-GG/nar-back-repo/actions
tags: [operations, ci, cd, github-actions, deploy]
timestamp: 2026-06-21T00:00:00Z
---

# 파이프라인 2종

| 워크플로 | 트리거 | 하는 일 |
|----------|--------|---------|
| **CI** (`.github/workflows/ci.yml`) | `pull_request` → main | `./gradlew build` (컴파일 + 테스트 + 패키징). 머지 게이트 |
| **Deploy** (`.github/workflows/deploy.yml`) | `push` → main | Gradle 빌드 → Docker 이미지 → Docker Hub → SSH로 EC2 배포 → `/v3/api-docs` 헬스체크 |

# PR 검증 CI

- 2026-06-21 추가. 그 전엔 `deploy.yml`이 `push:main`에서만 돌고 `-x test`로 **테스트를 건너뛰어** PR 게이트가 없었다.
- CI는 MySQL 통합 테스트를 러너의 Docker로 Testcontainers 구동한다.
- 풀컨텍스트 스모크 테스트(`NarApplicationTests`)는 로컬 시크릿이 필요해 clean checkout에서 자동 skip된다.

# 브랜치 보호 (main)

- **PR 필수** (승인 0명 — 솔로 self-merge 허용), 직접 push 금지
- 필수 상태 체크: **`build-and-test`** (strict — 최신 main 기준 통과 필요)
- force-push·브랜치 삭제 금지, 대화 해결 필수
- 관리자 우회는 허용(긴급 escape hatch)

→ 워크플로 전반은 [브랜치 워크플로](/operations/branch-workflow.md), 배포 시크릿은 [환경변수](/operations/environment.md) 참조.

# 운영 로그

배포된 컨테이너 로그는 [Dozzle](/operations/monitoring.md)에서 본다.

# Citations

[1] [CLAUDE.md — CI/CD](/references/claude-md.md)
[2] [GitHub Actions](https://github.com/NAR-GG/nar-back-repo/actions)
