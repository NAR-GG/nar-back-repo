---
type: Operations
title: 모니터링 (Dozzle)
description: 운영 컨테이너 로그를 Dozzle로 본다.
resource: https://api.nar.kr/dozzle
tags: [operations, monitoring, logs, dozzle]
timestamp: 2026-06-21T00:00:00Z
---

# 운영 로그

배포된 컨테이너의 실시간 로그는 **Dozzle**에서 본다: `https://api.nar.kr/dozzle`.

# 헬스체크

배포 파이프라인은 `/v3/api-docs` 엔드포인트로 헬스체크한다. → [CI/CD](/operations/ci-cd.md)

# API 문서

앱 기동 후:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`

# Citations

[1] [CLAUDE.md — CI/CD (Dozzle)](/references/claude-md.md)
