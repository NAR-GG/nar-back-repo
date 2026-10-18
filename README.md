# Warding · NAR.GG Backend

> 응원하는 선수와 팀의 순간을 놓치지 않게 — LoL e스포츠 알림 앱 **와딩(Warding)** 과 데이터 분석 웹 [**NAR.GG**](https://nar.kr) 의 백엔드

이 레포 하나가 두 서비스를 함께 서빙합니다.

| 서비스 | 형태 | 역할 |
|---|---|---|
| **Warding** (메인) | iOS · Android 앱 | 구독한 선수 솔랭·팀 경기 실시간 알림, 잠금화면 라이브 카드, 평점·커뮤니티 |
| **NAR.GG** | 웹 ([nar.kr](https://nar.kr)) | 챔피언 조합·매치업 승률, 경기 일정·기록, 팀/선수 통계 |

## Warding 핵심 기능

### 실시간 구독 알림
- **선수 솔랭 알림** — 구독한 프로 선수가 솔로랭크 게임을 시작·종료하면 푸시. 알림에서 바로 응원하고 응원 합계를 함께 보여줍니다
- **팀·경기 알림** — 세트 시작/종료와 킬·바론·드래곤·타워·억제기 같은 라이브 이벤트를, 이벤트 종류별로 켜고 끌 수 있습니다
- **방해 금지 시간** — 사용자가 정한 시간대에는 푸시를 보내지 않고 알림함에만 쌓습니다. 서버만으로 적용돼 앱 업데이트가 필요 없습니다
- **알림함** — 받은 알림 모아보기·읽음 처리. 타입별 생성량과 열람률에 맞춘 보존 정책으로 무한 증가를 막습니다

### iOS Live Activity
- 경기 진행 상황을 잠금화면·다이내믹 아일랜드 카드로 실시간 갱신 (APNs 직결)
- push-to-start(iOS 17.2+)로 서버가 카드를 직접 생성

### 경기 · 평점 · 커뮤니티
- 경기 일정 캘린더, 라이브 경기 데이터, 세트별 기록
- 경기 단위 선수 평점과 코멘트
- 커뮤니티: 게시글·댓글·투표·좋아요·스크랩, 신고·차단과 신고 임계 알림

### 운영
- 공지(앱 전용 페이지 포함)와 광고(배너·스플래시·팝업)를 백오피스에서 관리
- 앱스토어·플레이스토어 심사/배포 상태와 신규 리뷰를 디스코드로 수집
- 소셜 로그인(카카오·네이버·구글·애플) + JWT

## NAR.GG 웹 기능

- **챔피언 조합 / 1:1 매치업 분석** — 밴픽과 라인 구도 판단을 위한 조합·매치업 승률과 핵심 지표
- **경기 일정 / 결과** — 날짜별·리그별 일정과 이전/오늘/다음 경기 흐름
- **경기 상세 기록** — 세트별·팀별 주요 지표
- **팀 / 선수 통계** — 팀 순위·지표, 선수 카드와 프로필 통계
- **메타 / 트렌드** — 패치·시즌 기준 TOP 챔피언·선수 집계
- **카카오 챗봇 스킬** — 카카오톡에서 경기 일정 조회

## 아키텍처

```
Warding 앱 · nar.kr
   │
   ▼  Cloudflare (TLS)
cloudflared ──> Traefik ──> nar-web 파드 ──────────┐
                                                   ├──> MySQL 8.4
                         nar-scheduler 파드 ───────┘
                          ├─ LoL Esports / Riot API 폴링 (라이브 경기·솔랭)
                          ├─ FCM 푸시 · APNs Live Activity
                          └─ 디스코드 운영 알림
```

- 전 구간이 맥미니 M1 홈서버의 **k3s** 위에서 돕니다. 배포는 GitHub Actions → Docker Hub → **ArgoCD**(GitOps)
- 웹과 스케줄러는 **같은 이미지**를 쓰고 `APP_SCHEDULING_ENABLED` 로만 역할이 갈립니다
- 스케줄러는 **리더 리스**(`scheduler_lease`)로 언제나 하나만 잡을 실행합니다. 덕분에 경기 중에도 무중단 배포가 됩니다
- 푸시 발송은 전부 **DB 멱등**이라 파드가 교체돼도 알림이 중복되거나 유실되지 않습니다

자세한 내용은 [`infra/README.md`](infra/README.md) 와 [`docs/adr/`](docs/adr/) 를 참고하세요.

## 기술 스택

| 분야 | 기술 |
|---|---|
| Backend | Java 21 (가상 스레드), Spring Boot 3.5, Spring Security, JPA · jOOQ |
| Database | MySQL 8.4, Flyway, Caffeine 캐시 |
| Push | Firebase Cloud Messaging, APNs (Live Activity) |
| Infra | 맥미니 M1 홈서버 · k3s · ArgoCD · Cloudflare Tunnel |
| 관측 | Prometheus · Loki · Grafana, Sentry, Uptime Kuma |
| 외부 연동 | LoL Esports API, Riot API, YouTube Data API, App Store Connect, Google Play |

## 로컬 실행

```bash
docker-compose up -d            # 로컬 MySQL (3308)
./gradlew bootRun               # dev 프로파일
./gradlew test
```

- API 문서: `http://localhost:8080/swagger-ui.html`
- 로컬(dev)에서는 스케줄러가 꺼져 있습니다. 운영과 API 키·FCM 자격증명을 공유하므로 켤 때 주의하세요
- 환경 변수와 개발 규칙은 [`CLAUDE.md`](CLAUDE.md) 에 정리되어 있습니다

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
