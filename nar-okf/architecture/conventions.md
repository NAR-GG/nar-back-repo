---
type: Architecture Pattern
title: 코드 컨벤션
description: 생성자 주입, DTO 빌더, 커스텀 예외, DTO projection, 배치 insert 등 핵심 규약.
tags: [architecture, conventions, code-style]
timestamp: 2026-06-21T00:00:00Z
---

# 의존성 주입

- Lombok `@RequiredArgsConstructor`로 **생성자 주입**한다. 필드 주입 금지.

# DTO

- DTO는 `@Data` + `@Builder` 사용.
- 조회는 `@Query` + DTO projection(`select new ...`)으로 N+1을 피하고 전송량을 줄인다. → [영속성](/architecture/persistence.md)

# 예외 처리

- 커스텀 예외는 `CustomException`을 상속하고 `ErrorCode` enum을 쓴다.
- 전역 처리는 `GlobalExceptionHandler`가 담당한다.

# 배치

- 배치 insert는 JDBC batch size **50**.

# 언어

- 문서·계획·주석 등 모든 서술형 산출물은 **한국어**로 작성한다.

# Citations

[1] [CLAUDE.md — Key Conventions](/references/claude-md.md)
