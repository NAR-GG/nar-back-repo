# NAR.GG 백엔드 지식 번들

LoL e스포츠 분석 서비스 **NAR.GG**의 Spring Boot 백엔드 지식 번들. 아키텍처, 모듈, 운영(배포·DB·CI), 작업 절차를 OKF(Open Knowledge Format) 형식으로 정리한다.

이 번들은 [`CLAUDE.md`](references/claude-md.md)를 1차 출처로 하며, 그것을 **대체하지 않고 탐색·연결을 보강**한다. 같은 사실을 두 곳에 베끼지 않는다.

# 시작점

* [프로젝트 개요](overview.md) - NAR.GG 백엔드가 무엇이고 어떻게 구성되는지
* [References](references/) - CLAUDE.md, GitHub 프로젝트 보드 등 외부 출처

# 아키텍처

* [Architecture](architecture/) - 레이어 구조, 영속성(JPA/Flyway/ES), 캐시, 스케줄링, 컨벤션

# 모듈

* [Modules](modules/) - lolesports·analysis·monitor·schedule·search·youtube·riot·participant 등 도메인 모듈

# 운영

* [Operations](operations/) - CI/CD, 운영 DB 베이스라인, 브랜치 워크플로(TBD), 환경변수, 모니터링

# 플레이북

* [Playbooks](playbooks/) - 새 기능 추가, Flyway 마이그레이션 추가 절차

# 도구

* `viz.html` - 이 번들의 인터랙티브 그래프 뷰 (`python3 gen_viz.py .`로 재생성)
* `sync_github.py` - GitHub 보드(#2) → [references/github-project.md](references/github-project.md) 단방향 미러
