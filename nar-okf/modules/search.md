---
type: Module
title: search
description: Elasticsearch 색인과 전문(fulltext) 검색.
tags: [module, search, elasticsearch, stub]
timestamp: 2026-06-21T00:00:00Z
---

# 상태

🟡 Stub — 추후 상세 보강.

# 역할

Elasticsearch에 문서를 색인하고 전문 검색을 제공한다. 검색 문서는 별도 리포지토리 설정으로 관리된다. → [영속성](/architecture/persistence.md)

# 코드 위치

`app/search/`

# 환경

`ELASTICSEARCH_URI` 환경변수로 연결. → [환경변수](/operations/environment.md)

# Citations

[1] [CLAUDE.md — Key Modules: search](/references/claude-md.md)
