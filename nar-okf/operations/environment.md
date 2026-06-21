---
type: Operations
title: 환경변수
description: 자격증명·외부 API 키·기능 플래그를 환경변수로 주입한다. 하드코딩 금지.
tags: [operations, environment, secrets, feature-flags, config]
timestamp: 2026-06-21T00:00:00Z
---

# 원칙

모든 자격증명·외부 API 키는 **환경변수로 주입**하며 코드에 하드코딩하지 않는다. 배포 시 GitHub Actions가 시크릿을 주입한다. → [CI/CD](/operations/ci-cd.md)

# 주요 변수

| 분류 | 변수 |
|------|------|
| DB | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| 검색 | `ELASTICSEARCH_URI` |
| 외부 API | `YOUTUBE_API_KEY`, `LOL_ESPORTS_KEY`, `RIOT_API_KEY` |
| 알림 | `DISCORD_WEBHOOK_URL`, `DISCORD_PLAYER_WEBHOOK_URL` |
| 데이터 | `GOOGLE_DRIVE_CSV_ID` |
| 인증 | `JWT_SECRET` |
| 이미지 | `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` |
| 푸시 | `FIREBASE_MESSAGING_ENABLED`, `GOOGLE_APPLICATION_CREDENTIALS` |

# 기능 플래그

스케줄·외부 연동을 켜고 끄는 플래그:

- `RIOT_MONITOR_ENABLED`, `RIOT_API_ENABLED` — Riot 라이브 모니터/API → [monitor](/modules/monitor.md), [스케줄링](/architecture/scheduling.md)
- 미완성 기능을 트렁크에 OFF로 머지할 때 이 패턴을 쓴다. → [브랜치 워크플로](/operations/branch-workflow.md)

# Citations

[1] [CLAUDE.md — Environment Variables](/references/claude-md.md)
