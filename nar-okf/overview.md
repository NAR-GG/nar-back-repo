---
type: Project Overview
title: NAR.GG 백엔드
description: LoL e스포츠 분석 서비스의 Spring Boot 백엔드. 웹·모바일 두 프론트에 API를 제공한다.
resource: https://github.com/NAR-GG/nar-back-repo
tags: [spring-boot, java, esports, lol, backend]
timestamp: 2026-06-21T00:00:00Z
---

# 개요

**NAR.GG**는 LoL(리그 오브 레전드) e스포츠 분석 서비스다. 챔피언 조합, 매치업 통계, 경기 일정, 팀 성적 지표를 추적한다. 이 저장소(`NAR-GG/nar-back-repo`)는 그 **백엔드**로, 두 프론트엔드 — 나르지지 웹 프론트와 [`warding`](https://github.com/NAR-GG/warding-mobile-repo) Flutter 모바일 앱 — 에 공통 API를 제공한다.

# 기술 스택

| 항목 | 내용 |
|------|------|
| 프레임워크 | Spring Boot 3.5.3 (Java 17) |
| DB | MySQL 8 + [JPA/Hibernate](/architecture/persistence.md) |
| 마이그레이션 | [Flyway](/architecture/persistence.md) (운영 [baseline V30](/operations/db-baseline.md)) |
| 검색 | Elasticsearch |
| 캐시 | [Caffeine 2-tier](/architecture/caching.md) |
| 스케줄링 | [5-thread pool](/architecture/scheduling.md) |
| 배포 | [GitHub Actions → Docker → EC2](/operations/ci-cd.md) |

# 핵심 규칙 (요약)

- 레이어는 api / app / domain / config / common 으로 나뉜다 → [레이어 구조](/architecture/layered-structure.md)
- `open-in-view: false` — 명시적 fetch 필수, N+1은 DTO projection으로 회피 → [영속성](/architecture/persistence.md), [컨벤션](/architecture/conventions.md)
- 마이그레이션은 **멱등**하게, 운영 V1~V30 객체 존재를 가정하지 않는다 → [DB 베이스라인](/operations/db-baseline.md)
- `main`이 유일한 트렁크, 모든 작업은 단기 브랜치 → main PR (CI 통과 필수) → [브랜치 워크플로](/operations/branch-workflow.md)
- 자격증명은 전부 환경변수로 주입, 하드코딩 금지 → [환경변수](/operations/environment.md)

# 모듈 영역

도메인 모듈별 상세는 [Modules](/modules/) 참조. 핵심: [lolesports](/modules/lolesports.md), [analysis](/modules/analysis.md), [monitor](/modules/monitor.md), [schedule](/modules/schedule.md), [search](/modules/search.md), [youtube](/modules/youtube.md), [riot](/modules/riot.md), [participant](/modules/participant.md).

# 진행 상황

최신 진행 상황·남은 작업은 [GitHub 프로젝트 보드](/references/github-project.md)와 [CLAUDE.md](/references/claude-md.md)를 단일 출처로 본다. 이 번들엔 진행 상황을 중복 기록하지 않는다.

# Citations

[1] [nar-back-repo (GitHub)](https://github.com/NAR-GG/nar-back-repo)
[2] [CLAUDE.md](/references/claude-md.md)
