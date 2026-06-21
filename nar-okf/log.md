# Bundle Update Log

## 2026-06-21
* **Initialization**: NAR.GG 백엔드 지식 번들 생성 (OKF v0.1). warding-okf 컨벤션을 백엔드에 맞춰 적용.
* **Creation**: [개요](/overview.md), [아키텍처](/architecture/), [모듈](/modules/), [운영](/operations/), [플레이북](/playbooks/), [레퍼런스](/references/) 디렉토리 구성.
* **Scope**: 골격 + 핵심 개념 우선 작성. architecture·operations·playbooks는 풀 작성, modules는 index + 핵심 도메인 stub(이후 점진 풍부화).
* **Note**: 진행 상황·TODO는 [GitHub 프로젝트 보드(#2)](/references/github-project.md)와 [CLAUDE.md](/references/claude-md.md)를 단일 출처로 한다. 번들은 중복 기록하지 않는다.
* **Automation**: `gen_viz.py`(시각화), `sync_github.py`(보드 #2 → `references/github-project.md` 단방향 미러) 추가.
