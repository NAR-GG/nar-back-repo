# NAR.GG 
  
[LOL 대회 데이터 기반 분석 서비스](https://nar.kr)

리그 오브 레전드 e스포츠 데이터를 기반으로 챔피언 조합 분석, 매치업 승률, 경기 일정/기록 등을 확인할 수 있는 서비스입니다.  

## 기술 블로그
- [멀티 스케줄러 환경에서의 데드락 발생과 해결](https://changha-dev.github.io/posts/deadlock-1/)
- [DataTransfer-Out-Bytes 폭증으로 인한 과금 요소 분석 및 해결](https://changha-dev.github.io/posts/troubleshooting-1/)
- [분산환경에서의 배치 스케줄링 아키텍처 설계 및 비교](https://changha-dev.github.io/posts/distribute-batch-architecture)
- [2026년 새해맞이 나르지지 방향성](https://changha-dev.tistory.com/200)
- [SQLite와 MySQL 동시쓰기 비교해보기](https://changha-dev.tistory.com/199)
- [MySQL에서 SQLite로 마이그레이션 한 이유](https://changha-dev.tistory.com/198)
- [반복되는 일정 서비스 API콜에 대한 캐시 적용 및 전략](https://changha-dev.tistory.com/197)
- [DTO 프로젝션을 활용한 일정 서비스 성능 개선](https://changha-dev.tistory.com/196)
- [8만 건 데이터 DB 마이그레이션 자동화 구축](https://changha-dev.tistory.com/195)
- [OOM 원인 API, DB 최적화로 해결하기](https://changha-dev.tistory.com/194)

## 기술 스택

| 분야      | 기술 |
|-----------|------|
| Backend   | Java, Spring Framework |
| Database  | MySQL 8.4 (맥미니 호스트) |
| Infra     | 맥미니 M1 홈서버 · k3s · ArgoCD · Cloudflare Tunnel ([`infra/`](infra/README.md)) |
| CI/CD     | GitHub Actions → Docker Hub → ArgoCD (GitOps) |
| Data      | 6시간 주기 데이터 자동 업데이트 |

## 핵심 기능

### 챔피언 조합/1:1 매치업 분석
- 목적: 밴픽 및 라인 구도 의사결정을 위해 조합 승률, 매치업 승률, 핵심 지표를 제공합니다.

### 경기 일정/결과 조회
- 목적: 날짜별/리그별 경기 일정과 결과를 빠르게 확인하고, 이전/오늘/다음 경기 흐름을 파악할 수 있게 합니다.

### 경기 상세 기록
- 목적: 특정 경기의 세트별/팀별 주요 지표를 제공해 경기 내용과 승패 요인을 분석할 수 있게 합니다.

### 팀/선수 통계 분석
- 목적: 팀 순위, 팀 지표, 선수 카드/프로필 통계를 통해 시즌 퍼포먼스를 비교 분석할 수 있게 합니다.

### 메타/트렌드 집계
- 목적: 패치와 시즌 기준으로 TOP 챔피언/선수 지표를 집계해 현재 메타를 빠르게 파악할 수 있게 합니다.

### 데이터 자동 업데이트
- 목적: 주기적 수집/동기화를 통해 최신 경기/통계 데이터를 안정적으로 제공합니다.
