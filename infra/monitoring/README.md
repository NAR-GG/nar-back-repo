# 관측 스택 (Prometheus · Loki · Grafana)

맥미니 홈서버를 관측 허브로 두고 프로덕션 앱(EC2)과 DB(Oracle VM)의 메트릭·로그를 한 곳에서 본다.

이 디렉토리는 **원본 보관용이다. 자동 배포가 아니다.** 서버 설정을 바꿨으면 여기에도 반영하고,
기기를 새로 세울 때는 여기서 복사한다.

## 토폴로지

```
EC2 nar-app (100.88.94.95)
  ├ nginx :9105        ──pull──>  Prometheus ─┐
  └ Grafana Alloy      ──push──>  Loki ───────┼─> Grafana :3000
                                              │
Oracle VM nargg-vnic (100.101.232.109)        │
  └ mysqld_exporter :9104  ──pull─────────────┘

                          맥미니 (100.111.167.92)
```

**방향이 서로 반대다.** Prometheus는 맥미니가 긁어가고(pull), Loki는 EC2가 밀어넣는다(push).

전 구간이 Tailscale 위다. 공인 IP도 포트 개방도 없다. 그래서 **EC2 보안그룹에 9105를 열면 안 된다** —
Tailscale 트래픽은 이미 성립된 아웃바운드 WireGuard 세션에 실려 오므로 보안그룹을 거치지 않는다.
열면 인터넷에도 노출된다.

Colima VM 안 컨테이너에서 `100.x` 대역은 도달하지만 **MagicDNS 이름은 해석하지 못한다.**
그래서 스크랩 타깃과 push URL은 이름이 아니라 IP로 적는다.

## 접속

| | 주소 |
|---|---|
| Grafana | `https://grafana.nar.kr` (Cloudflare Access) 또는 `http://macmini:3000` |
| Uptime Kuma | `https://kuma.nar.kr` (Cloudflare Access) 또는 `http://100.71.240.23:3001` |
| Prometheus | `http://macmini:9090` |
| Loki | `http://macmini:3100` (직접 볼 일은 없다) |

Prometheus·Loki 는 Tailscale 안에서만 닿는다. Grafana·Kuma 는 Cloudflare Tunnel 로도
나가 있다(`infra/k8s/cloudflared.yaml`). **그 두 호스트는 Cloudflare Access 가 앞에서
막는 전제로 열었다** — Access 앱을 지우면 관리 UI 가 공개 인터넷에 그대로 남으므로,
정책을 풀 때는 ingress 규칙도 같이 지운다.

## 앱 메트릭이 9105를 거치는 이유

앱 컨테이너는 `127.0.0.1:8080|8083`에만 바인딩되고(블루-그린), `SecurityConfig`는
`/actuator/**`를 `permitAll`로 둔다. 즉 앱 자체에는 인증이 없다. 접근 제어를 전부 nginx가 한다.

- `sites-enabled/api.nar.kr` 443 블록에 `location /actuator/ { return 404; }` — 공개 도메인 차단
- `conf.d/nar-metrics.conf`(이 디렉토리의 `ec2/nginx-nar-metrics.conf`) — 9105 포트를
  Tailscale 대역(`100.64.0.0/10`)에만 열어 스크랩을 받는다

`upstream nar_backend`를 참조하므로 블루-그린 포트 전환(8080↔8083)을 자동으로 따라간다.
배포 스크립트가 재작성하는 파일은 `conf.d/nar-upstream.conf` 뿐이라 이 설정은 배포에 덮이지 않는다.

**앞단 nginx가 없는 환경으로 앱을 옮기면 이 전제가 깨진다.** 그때는 접근 제어를 앱으로 가져와야 한다.

## 대시보드

`macmini/grafana/provisioning/dashboards/` 아래 JSON 이 진실의 원천이다. `allowUiUpdates: false`
라서 GUI 에서 고쳐도 30초 뒤 파일 내용으로 덮인다. **바꿀 때는 GUI 에서 실험한 뒤
`Export > Save to file` 로 JSON 을 뽑아 이 파일을 갱신하고 커밋한다.**

데이터소스 `uid` 는 `prometheus`, `loki` 로 고정한다. 대시보드 JSON 이 이 값을 참조하기 때문이다.
`uid` 없이 먼저 프로비저닝하면 Grafana 가 랜덤 값을 붙이고, 나중에 `uid` 를 지정하는 순간
`data source not found` 로 부팅이 실패한다(재시작 루프에 빠진다). `deleteDatasources` 로
먼저 지우고 다시 만들게 해두었다.

### NAR 서비스 개요 (`nar-overview.json`)

"지금 정상인가"를 30초 안에 판단하는 1층 대시보드다. 벤더 임포트 대시보드(4701 JVM,
12900 SpringBoot APM, 14057 MySQL)는 컴포넌트를 파고들 때 쓰는 참조용이고, 평소에 여는 건 이쪽이다.

**백엔드는 단일 배포지만 URI 로 서비스를 나눠서 본다.** 배포를 쪼개지 않고도 트래픽·에러율·응답시간을
서비스별로 분리할 수 있다.

| 분류 | URI |
|---|---|
| Warding 앱 | `/api/mobile/**` |
| 인증 (앱·웹 공통) | `/api/auth/**` |
| 백오피스 | `/api/admin/**` |
| 웹 | 위 셋과 `/actuator/**`, 노이즈를 제외한 나머지 |
| 노이즈 | `UNKNOWN`, `REDIRECTION`, `/**` (크롤러·스캐너), `/v3/**`·`/swagger-ui**` |

`/v3/api-docs` 는 배포 스크립트의 헬스체크가 때리는 경로다(`deploy.yml`). 사용자 트래픽이 아닌데
p95 가 높게 잡혀 "느린 엔드포인트 TOP 5" 상단을 차지하므로 노이즈로 뺀다. Swagger UI 도 같다.

**인증을 웹에 넣으면 안 된다.** `/api/auth/**` 는 앱 사용자가 대부분이라, 웹으로 세면 웹 트래픽이
4배로 부풀려진다(실측: 웹 0.12/s 인데 인증 포함 시 0.62/s). 그래서 별도 분류로 뺐다.

2026-08-15 실측 비율은 Warding 91%, 인증 나머지 대부분, 웹 7%, 백오피스 0 이다.

응답시간은 **p50/p95/p99** 로 본다. 평균은 꼬리를 감춘다 — 요청 1%가 5초 걸려도 평균은 거의
안 움직인다.

이게 되려면 앱이 히스토그램 버킷(`_bucket` 시리즈)을 내보내야 한다. Spring Boot 기본값은
`_count` 와 `_sum` 뿐이라 평균밖에 못 구한다. `application.yml` 의
`management.metrics.distribution` 블록이 그 설정이고, **이 설정 없이는 응답시간 패널이 전부 빈다.**

버킷은 시리즈 수를 곱하므로 기대 범위를 10ms~10s 로 좁혀 카디널리티를 억제했다.
10ms 미만은 구분할 실익이 없고 10s 를 넘으면 어차피 다 같은 장애다.

## 재구축

### 맥미니

```bash
brew install colima docker docker-compose
colima start --cpu 6 --memory 10 --disk 100 --vm-type vz --mount-type virtiofs
brew services start colima

mkdir -p ~/monitoring && cp -r macmini/* ~/monitoring/
cd ~/monitoring && docker compose up -d
```

대시보드는 Grafana API로 넣는다. 4701(JVM Micrometer), 12900(SpringBoot APM)은 import API로
그대로 들어가지만, **14057(MySQL)은 `__inputs`가 비어 있고 `id`가 박혀 있어 import API가 거부한다.**
`id`를 `null`로 바꿔 `/api/dashboards/db`에 POST해야 들어간다.

### EC2

```bash
sudo mkdir -p /etc/apt/keyrings/
wget -q -O - https://apt.grafana.com/gpg.key | gpg --dearmor | sudo tee /etc/apt/keyrings/grafana.gpg > /dev/null
echo "deb [signed-by=/etc/apt/keyrings/grafana.gpg] https://apt.grafana.com stable main" | sudo tee /etc/apt/sources.list.d/grafana.list
sudo apt-get update && sudo apt-get install -y alloy

sudo cp alloy-config.alloy /etc/alloy/config.alloy
sudo usermod -aG docker alloy          # 도커 소켓을 읽어야 한다
sudo systemctl restart alloy

sudo cp nginx-nar-metrics.conf /etc/nginx/conf.d/
sudo nginx -t && sudo nginx -s reload
```

### Oracle VM

```bash
V=0.20.0
curl -sSL -O https://github.com/prometheus/mysqld_exporter/releases/download/v${V}/mysqld_exporter-${V}.linux-amd64.tar.gz
tar xzf mysqld_exporter-${V}.linux-amd64.tar.gz
sudo install -m 0755 mysqld_exporter-${V}.linux-amd64/mysqld_exporter /usr/local/bin/

sudo useradd --system --no-create-home --shell /usr/sbin/nologin mysqld_exporter
sudo mkdir -p /etc/mysqld_exporter
# .my.cnf 작성: [client] user=exporter / password=... / socket=/var/run/mysqld/mysqld.sock
sudo chown -R mysqld_exporter:mysqld_exporter /etc/mysqld_exporter
sudo chmod 600 /etc/mysqld_exporter/.my.cnf

sudo cp mysqld_exporter.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now mysqld_exporter
```

exporter 계정은 읽기 전용으로 만든다.

```sql
CREATE USER 'exporter'@'localhost' IDENTIFIED BY '<비번>' WITH MAX_USER_CONNECTIONS 3;
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'localhost';
```

TCP 대신 유닉스 소켓을 쓴다. MySQL 8 기본 인증(`caching_sha2_password`)이 평문 TCP에서
RSA 키 교환을 요구하는데, 소켓은 그 과정을 건너뛴다.

## 시크릿

이 디렉토리에는 시크릿이 없다.

- Grafana 관리자 비밀번호 — 맥미니 `grafana-data` 볼륨 안
- mysqld_exporter 비밀번호 — EC2가 아닌 Oracle VM의 `/etc/mysqld_exporter/.my.cnf` (0600)

Tailscale IP가 들어가지만 사설망 주소라 공개돼도 접근되지 않는다.

## 밟았던 함정

1. **`proxy_set_header Host $host;`를 빼면 400 Bad Request가 난다.** nginx 기본값은 Host 헤더에
   `$proxy_host`(=`nar_backend`)를 넣는데, 호스트명에 언더스코어는 RFC상 무효라 Tomcat이 거부한다.

2. **`sites-available`과 `sites-enabled`가 심볼릭 링크가 아니다.** `sites-available` 쪽은
   `proxy_pass localhost:8080`이 하드코딩된 옛 사본이다. 활성 설정은 `nginx -T`(머지된 최종 설정)로 확인한다.

3. **`sites-enabled/`에 `.bak` 파일을 두면 안 된다.** nginx가 그 디렉토리의 모든 파일을 include해서
   server 블록이 중복된다. 백업은 다른 경로에 둔다.

4. **Promtail은 2026년 3월 2일 EOL이다.** 인터넷 문서 대부분이 Promtail 기준이라 그대로 따라하면
   지원이 끝난 스택을 깐다. Grafana Alloy를 쓴다.

5. **Loki는 컨테이너가 `Up`이어도 `/ready`가 한동안 503을 낸다**(ingester 워밍업 15초).
   부팅 후 전체가 준비되기까지 약 60초 걸린다. 알림 규칙을 걸 때 이 구간을 오탐으로 잡지 않게 한다.

## 자주 쓰는 쿼리

```promql
# 버퍼풀 히트율 (구간별) — 누적값만 보면 최근 상태를 놓친다
1 - rate(mysql_global_status_innodb_buffer_pool_reads[5m])
  / rate(mysql_global_status_innodb_buffer_pool_read_requests[5m])

jvm_memory_used_bytes{area="heap"}
hikaricp_connections_active
```

```promql
# 잡이 멎었나 — 마지막 성공 이후 경과 시간 (라벨은 job 이 아니라 scheduler_job 이다.
# job·instance 는 Prometheus 예약 라벨이라 메트릭이 같은 이름을 쓰면 exported_job 으로 밀린다)
time() - nar_scheduler_last_success_epoch_seconds

# 30분 주기인 MATCH_SYNC 가 한 시간 넘게 성공 못 함
time() - nar_scheduler_last_success_epoch_seconds{scheduler_job="MATCH_SYNC"} > 3600

# 재시작 후 한 번도 못 돈 잡 (시리즈 자체가 없으므로 absent 로 잡는다)
absent(nar_scheduler_last_success_epoch_seconds{scheduler_job="MATCH_SYNC"})

# 상류 CSV 정체 — 신규 게임 0건 연속
nar_scheduler_zero_new_games_streak > 3

# 잡별 실패율
sum by (scheduler_job) (rate(nar_scheduler_runs_total{outcome="failure"}[30m]))
  / sum by (scheduler_job) (rate(nar_scheduler_runs_total[30m]))

# 잡 소요 시간 p95
histogram_quantile(0.95, sum by (le, scheduler_job) (rate(nar_scheduler_duration_seconds_bucket[30m])))
```

```logql
{container="nar-gg-blue"} | detected_level="error"

# 1초 넘는 요청. LoggingFilter 가 요청마다 Duration 을 남긴다
{container="nar-gg-blue"} |= "[END]" | pattern "<_>Duration: <dur>ms" | dur > 1000

# 에러 발생률 — 알림 규칙에 쓸 식
sum(rate({container="nar-gg-blue"} | detected_level="error" [5m]))
```

블루-그린 배포라 컨테이너 이름이 `nar-gg-blue`와 `nar-gg-green`을 오간다. 현재 어느 쪽인지는
`container` 라벨 값으로 확인한다.
