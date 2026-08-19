# 맥미니 홈서버 구성

EC2 비용을 없애려고 앱을 집 맥미니(M1 16GB)로 옮겼고, **2026-08-19 EC2 를 종료했다**
(최종 스냅샷 `nar-ec2-final` 보존, EIP 릴리스). 이 디렉토리는 그 기계의
설정 **원본 보관소**다. `infra/monitoring` 과 같은 규칙 — **자동 배포가 아니다.**
서버 설정을 바꿨으면 여기에도 반영하고, 기계를 새로 세울 때는 여기서 복사한다.

## 지금 상태 (2026-08-18)

컷오버 완료. **실서비스가 맥미니에서 돈다.** DB 도 맥미니가 원본이다.

```
사용자 ──> Cloudflare ──> cloudflared(터널) ─┬─> nginx :8081 ──> 앱 컨테이너(docker)
                                             │                        │
                                             │      맥미니 MySQL 8.4 (원본) <┘
                                             │
                                             └─> :30443 ──> ArgoCD (k3s)
                                                  ↑ Cloudflare Access 가 앞에서 막는다
```

| 호스트 | 가는 곳 |
|---|---|
| `api.nar.kr` | Traefik :30082 → k3s 파드. 실트래픽 (2026-08-19 전환) |
| `argocd.nar.kr` | k3s NodePort :30443 → ArgoCD. **Cloudflare Access 필수** |
| `home.nar.kr` | `api.nar.kr` 과 동일 |

**웹 트래픽은 파드가, 스케줄러는 아직 docker 컨테이너가 돈다.** 스케줄러 이관 전까지
docker 를 끄면 안 된다 — 라이브 폴링·푸시·동기화가 전부 멈춘다.
nginx(8081)에 남은 역할은 Prometheus 메트릭 프록시(9105)뿐이다. 스케줄러 이관과
Prometheus 타깃 변경이 끝나면 nginx 는 은퇴한다.

춘천 MySQL 은 역방향 복제로 따라오는 롤백 안전망이다(`scripts/cutover-reverse-repl.sh`).

## 파일

| 경로 | 실제 위치 |
|---|---|
| `nginx/nar.conf` | `/opt/homebrew/etc/nginx/servers/nar.conf` |
| `nginx/nar-upstream.conf` | `/opt/homebrew/etc/nginx/servers/nar-upstream.conf` — **배포 스크립트가 덮어쓴다** |
| `mysql/my.cnf` | `/opt/homebrew/etc/my.cnf` |
| `cloudflared/config.yml` | `/opt/homebrew/etc/cloudflared/config.yml` |
| `launchd/com.nar.cloudflared.plist` | `~/Library/LaunchAgents/` |
| `launchd/com.nar.dbbackup.plist` | `~/Library/LaunchAgents/` |
| `scripts/nar-db-backup.sh` | `~/nar/nar-db-backup.sh` |
| `scripts/cutover-reverse-repl.sh` | 노트북에서 실행 (양쪽 SSH 필요) |
| `scripts/nar-watchdog.sh` | **춘천 `instance-elasticsearch`** 의 `/usr/local/bin/` |

## 여기 없는 것 — 비밀값

전부 파일 참조로만 쓴다. 저장소에는 값이 없다.

```
~/nar/.backup-par           OCI 버킷 PAR (쓰기 전용 서명 URL)
~/nar/.discord-webhook      실패 알림
~/nar/.reverse-repl-pw      역방향 복제 계정 (춘천에도 /root/.nar-reverse-repl-pw 로 배치)
~/.cloudflared/*.json       터널 자격증명
~/nar/secrets/              firebase-service-account.json, apns-auth-key.p8 (배포가 심는다)
~/nar/.cf-api-token         Cloudflare API 토큰 (Access·DNS·Tunnel 편집)
~/nar/.cf-account-id        계정 ID (토큰에 계정 조회 권한이 없어 따로 적어 둔다)
~/nar/.cf-team-domain       nar-gg.cloudflareaccess.com
~/nar/.cf-access-app-id     ArgoCD Access 앱 ID
```

## ArgoCD 를 공개한 방식 — Access 가 전부다

`argocd.nar.kr` 은 공개 인터넷에 열려 있다. 관리자 UI 를 지키는 건 ArgoCD 로그인이
아니라 **그 앞의 Cloudflare Access** 다. Access 앱을 지우거나 정책을 풀면 UI 가
그대로 노출되므로, 그럴 때는 터널 ingress 에서 이 호스트도 같이 내려야 한다.

```
사용자 → Access(이메일 일회용 코드) → ArgoCD 로그인(admin) → UI
```

무인증 요청은 `/api/v1/session` 같은 API 경로까지 전부 302 로 튕긴다(검증함).

**API 로 조직을 만들면 로그인 수단이 하나도 없다.** 대시보드로 만들 때와 달리
One-time PIN 이 자동으로 붙지 않아서, 로그인 화면이
`There are no login methods available for this account` 로 죽는다.
`onetimepin` 타입 IdP 를 따로 만들어야 한다.

```bash
T=$(tr -d '[:space:]' < ~/nar/.cf-api-token); ACC=$(cat ~/nar/.cf-account-id)
curl -s -X POST -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  "https://api.cloudflare.com/client/v4/accounts/$ACC/access/identity_providers" \
  -d '{"name":"One-time PIN","type":"onetimepin","config":{}}'
```

### 백도어

Cloudflare 쪽 설정을 잘못 건드려 못 들어가게 되면 Tailscale 로 우회한다.
Access 와 무관한 경로다.

```
https://macmini.tail97b60c.ts.net     tailscale serve → 127.0.0.1:30443
```

## cron 을 쓰지 않는다

**이 기계에서 crontab 항목이 발화하지 않는 것을 확인했다**(2026-08-17, 테스트 항목을 2분 뒤로
걸었는데 안 돌았다). macOS 의 cron 은 조용히 안 도는 경우가 있어, 백업처럼 실패를 모르면
치명적인 작업에는 쓰지 않는다. launchd 로 통일하고, 캘린더 발화까지 실제로 검증했다.

## 헤드리스 macOS 전제

- **FileVault 는 꺼야 한다.** 켜면 재부팅 후 디스크가 잠긴 채 멈춰 SSH 도 안 열린다.
- `brew services`(Colima·nginx·mysql@8.4)와 사용자 LaunchAgent 는 **자동 로그인이 전제**다.
- `ssh host "명령"` 은 `.zshenv` 만 읽는다. brew PATH 를 거기 넣어야 원격 명령에서 docker 를 찾는다.

## 포트

| 포트 | 용도 |
|---|---|
| 8081 | nginx (cloudflared 가 여기로 넘긴다) |
| 8080 / 8083 | 앱 컨테이너 블루-그린 |
| 8090 | brew nginx 기본 서버 (기본값 8080 이 앱과 겹쳐 옮겼다) |
| 3306 | MySQL — `127.0.0.1` + Tailscale 만. LAN 에는 안 연다 |

## 배포 함정

### 워크플로만 고치면 배포가 안 된다

`deploy-macmini.yml` 의 `paths-ignore` 에 `.github/**` 가 있다. 코드가 안 바뀌었는데
재배포할 이유가 없어서 넣은 것인데, **워크플로 자체를 고칠 때도 걸린다.**

2026-08-17 에 WhaTap 마운트 누락을 고쳐 머지했는데 배포가 안 돌았다. 수정은 main 에
있고 서버는 옛 설정 그대로인, 조용한 불일치 상태가 된다.

워크플로를 고쳤으면 수동으로 돌린다:

```bash
gh workflow run deploy-macmini.yml --ref main
```

### 배포가 실패해도 서비스는 안 죽는다 — 그래서 눈치채기 어렵다

블루-그린이라 새 컨테이너가 헬스체크에서 죽으면 구 컨테이너가 계속 트래픽을 받는다.
사용자 영향은 0 이지만 **그동안 머지한 코드가 반영되지 않는다.**

같은 날 배포가 두 번 연속 실패하는 동안 커밋 세 개가 밀렸다. 실행 중인 이미지 태그를
main 과 대조하면 바로 보인다:

```bash
ssh changha@macmini 'docker inspect $(docker ps --format "{{.Names}}" -f name=nar-gg | head -1) --format "{{.Config.Image}}"'
git log --oneline <그_태그>..origin/main
```

### 자동화는 이름이 아니라 IP 를 쓴다

Tailscale 기기 이름은 바뀔 수 있다. 실제로 `macmini` 가 `macmini-1` 로 바뀌어 사람이
쓰는 `ssh macmini`·`http://macmini:3000` 이 끊긴 적이 있다. 배포와 스크립트는 전부
`100.111.167.92` 를 써서 아무 영향이 없었다.

관측 스택 README 의 원칙과 같다 — 스크랩 타깃과 push URL 도 이름이 아니라 IP 다.

## 컷오버 때 손대야 하는 것

1. `mysql/my.cnf` 의 `read_only` / `super_read_only` 두 줄을 지우고, 런타임도 `SET GLOBAL` 로 푼다.
   **한쪽만 하면 안 된다** — 설정만 지우고 재기동을 안 하면 런타임 값이 남고, 런타임만 풀면
   다음 재기동에 앱이 깨진다.
2. `cloudflared/config.yml` 의 ingress 에 `api.nar.kr` 을 추가한다.
3. 저장소 변수 `APP_SCHEDULING_ENABLED` / `SPRING_FLYWAY_ENABLED` 를 `true` 로.
4. `scripts/cutover-reverse-repl.sh --apply` 로 역방향 복제를 건다(롤백 안전망).
