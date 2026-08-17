# 맥미니 홈서버 구성

EC2 비용을 없애려고 앱을 집 맥미니(M1 16GB)로 옮기는 중이다. 이 디렉토리는 그 기계의
설정 **원본 보관소**다. `infra/monitoring` 과 같은 규칙 — **자동 배포가 아니다.**
서버 설정을 바꿨으면 여기에도 반영하고, 기계를 새로 세울 때는 여기서 복사한다.

## 지금 상태 (2026-08-17)

실서비스는 아직 EC2 다. 맥미니는 **그림자**로 돈다.

```
사용자 ──> Cloudflare ──> cloudflared(터널) ──> nginx :8081 ──> 앱 컨테이너
                                                                    │
                                        맥미니 MySQL 8.4 (복제본) <──┘
                                                    ↑ 복제
                                        춘천 MySQL 8.0 (원본, 실서비스)
```

`home.nar.kr` 이 그림자, `api.nar.kr` 은 여전히 EC2 다.

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

## 컷오버 때 손대야 하는 것

1. `mysql/my.cnf` 의 `read_only` / `super_read_only` 두 줄을 지우고, 런타임도 `SET GLOBAL` 로 푼다.
   **한쪽만 하면 안 된다** — 설정만 지우고 재기동을 안 하면 런타임 값이 남고, 런타임만 풀면
   다음 재기동에 앱이 깨진다.
2. `cloudflared/config.yml` 의 ingress 에 `api.nar.kr` 을 추가한다.
3. 저장소 변수 `APP_SCHEDULING_ENABLED` / `SPRING_FLYWAY_ENABLED` 를 `true` 로.
4. `scripts/cutover-reverse-repl.sh --apply` 로 역방향 복제를 건다(롤백 안전망).
