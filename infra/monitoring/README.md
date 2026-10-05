# 관측 스택 (Prometheus · Loki · Grafana)

맥미니 홈서버가 관측 허브이면서 관측 대상이다. 앱 파드·DB·춘천 박스의 메트릭·로그를 한 곳에서 본다.

이 디렉토리는 **원본 보관용이다. 자동 배포가 아니다.** 서버 설정을 바꿨으면 여기에도 반영하고,
기기를 새로 세울 때는 여기서 복사한다.

## 토폴로지

```
맥미니 (100.111.167.92)
  ├ nar-web 파드           NodePort 30081 ──pull──>  Prometheus ─┐
  ├ nar-scheduler 파드      NodePort 30084 ──pull──>             │
  ├ kube-state-metrics 파드 NodePort 30085 ──pull──>             ├─> Grafana :3000
  ├ kubelet cAdvisor            :10250 ──pull──>                │   (grafana.nar.kr)
  ├ mysqld_exporter              :9104 ──pull──>                │        │
  └ Grafana Alloy                      ──push──>  Loki ─────────┘        └─> 디스코드
                                                                              (인프라 채널)
춘천 es-vnic (100.71.240.23)
  └ Uptime Kuma :3001  (kuma.nar.kr) — 맥미니를 밖에서 찌른다
```

**방향이 서로 반대다.** Prometheus 는 긁어가고(pull), Loki 는 Alloy 가 밀어넣는다(push).

스케줄러 파드가 별도 NodePort(30084)를 갖는 이유는 `nar-scheduler-service.yaml` 주석에 있다 —
#442 로 파드가 갈린 날 `nar_scheduler_*` 지표 36개가 파드를 따라 이사했는데 Prometheus 는
30081 만 긁고 있어서 오버뷰의 잡 패널이 No data 였다.

전 구간이 Tailscale 위다. 공인 IP 도 포트 개방도 없다. Grafana·Kuma 만 Cloudflare Tunnel 로
나가 있고 그 앞은 Cloudflare Access 가 막는다.

Colima VM 안 컨테이너에서 `100.x` 대역은 도달하지만 **MagicDNS 이름은 해석하지 못한다.**
그래서 스크랩 타깃과 push URL 은 이름이 아니라 IP 로 적는다.

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

## 앱 메트릭 접근 제어 — 9105 는 없어졌다

`SecurityConfig` 는 `/actuator/**` 를 `permitAll` 로 둔다. **앱 자체에는 인증이 없다.**
접근 제어는 전부 앞단이 한다. docker 시절엔 nginx 9105 프록시가 그 역할이었는데,
블루-그린 포트 전환(8080↔8083)을 따라가기 위한 장치였고 블루-그린이 사라지면서 같이 걷혔다(#422).

지금은 두 겹이다.

- **공개 도메인**: `traefik-routes.yaml` 의 `actuator-deny` 미들웨어가 `/actuator` 를 403 으로 막는다
  (`ipAllowList: 255.255.255.255/32` — 어떤 IP 도 매치되지 않는 대역)
- **스크랩**: Prometheus 가 NodePort 를 직접 긁는다. `nar-web` 30081, `nar-scheduler` 30084.
  NodePort 는 VM 안 + 호스트 루프백에서만 닿는다

**앞단이 없는 환경으로 앱을 옮기면 이 전제가 깨진다.** 그때는 접근 제어를 앱으로 가져와야 한다.

## 트레이스 (Tempo) — 요청 하나의 경로를 본다

메트릭(Prometheus)은 "느려졌다"를, 로그(Loki)는 "무슨 일이 있었다"를 알려준다. 트레이스는
**"이 요청이 어디서 몇 ms 를 썼나"** 를 보여준다 — 컨트롤러 → 서비스 → SQL N번 → 외부 API 가
한 줄 워터폴이다. WhaTap 해지 후 빠지는 트랜잭션·SQL 분석 자리를 메운다.

```
nar-web 파드 ─ OTel Java agent ──OTLP push──> Tempo :4318 ──> Grafana (Explore > Tempo)
```

방향은 Loki 와 같다(push). 파드 → `172.17.0.1:4318` (Prometheus 가 파드를 긁는 그 docker 브리지).
**서버 컨테이너(`tempo`)만 올려 두면 아무 영향이 없다** — 앱이 agent 를 붙이기 전까지 아무도 안 보낸다.

### 앱 쪽 (적용됨 — #552, 2026-10-05)

agent jar 는 WhaTap 과 같은 방식으로 hostPath 에 둔다(이미지 재빌드 불필요).

1. 서버의 `~/nar/otel/opentelemetry-javaagent.jar` (v2.32.0,
   `https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases`)
2. `nar-web.yaml` — `/otel` hostPath 마운트, `JAVA_TOOL_OPTIONS` 의 `-javaagent`, `OTEL_*` 환경변수.
   샘플링 10%(경기 시작 버스트 대비), 트레이스만 export(메트릭·로그는 Prometheus·Loki 가 맡는다).
   **끄기: `OTEL_SDK_DISABLED=true`.** jar 를 지우면 JVM 이 기동에 실패한다(CrashLoop).
3. 메모리: 붙인 직후 nar-web 이 1.1GiB → 1.37GiB 로 올랐다(limit 2Gi, Xmx 1024m). 워밍업이 섞인 값이라 안정값은 따로 본다.
4. **스케줄러 파드는 안 붙인다.** 폴링 span 이 너무 많다. 리더 리스·FCM 발송 구간을 보고 싶을 때
   샘플링을 낮춰 따로 붙인다.

> ⚠️ **`infra/**` 만 바뀐 머지는 자동 배포가 안 돈다**(`paths-ignore`). 매니페스트를 클러스터에 반영하려면
> `gh workflow run deploy-macmini.yml --ref main` 으로 직접 돌린다. 자세한 건 `infra/argocd/README.md`.

### 로그 ↔ 트레이스 연결

- 앱 로그가 `ERROR [<trace_id>,<span_id>] 1 --- ...` 로 찍힌다(`application-prod.yml` `logging.pattern.level`).
  OTel agent 가 MDC 에 `trace_id`·`span_id` 를 넣어 준다. 트레이스 밖에서는 `[,]` 로 빈다.
- Loki → Tempo: 로그 줄 옆에 **"Tempo 에서 보기"** 링크가 생긴다(derivedFields).
- Tempo → 로그: 트레이스 화면의 span 에서 로그 버튼(tracesToLogsV2, `|= "<traceId>"` 본문 검색).
- 샘플링 10% 라 **모든 로그에 연결되는 트레이스가 있는 건 아니다.** 느린 요청이 샘플에서 빠지면 로그에 id 는 있어도 Tempo 에 없다.

### 서버 쪽 처음 올릴 때

`docker compose up -d tempo` 한 줄이다. 새 볼륨에 쓰기 권한이 없으면 `permission denied` 로 죽는다
(컨테이너 uid 10001). 그때는 `docker run --rm -v monitoring_tempo-data:/v alpine chown -R 10001:10001 /v`.
볼륨 이름은 `docker volume ls` 로 확인한다.

**DB·시크릿·재부팅이 아니라 기존 컨테이너를 건드리지 않는 추가 하나다.** 그래도 Grafana 를 재시작해
데이터소스를 읽히므로(provisioning) 경기 창은 피한다.

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

## 알림 (→ 디스코드 인프라 채널)

2026-09-08 추가. **그 전까지 Grafana 알림이 아예 없었다** — provisioning 에 dashboards 와
datasources 만 있었다. 그래서 `traefik`·`cloudflared` 가 경기 시작마다 OOMKilled 로 죽은
16일치를 아무도 몰랐다(08-29 / 09-02 / 09-05 / 09-06, 전부 17시대). 앱 5xx 는 0건이라
대시보드는 계속 초록이었고, 죽은 것은 앱이 아니라 엣지 관문이었다.

| 파일 | 무엇 | Git |
|---|---|---|
| `grafana/provisioning/alerting/rules.yaml` | 알림 규칙 2개 | ✅ |
| `grafana/provisioning/alerting/contact-points.yaml` | 디스코드 웹훅 + 라우팅 | ❌ 서버만 |
| `grafana/provisioning/alerting/contact-points.yaml.example` | 위 파일 템플릿 | ✅ |

**채널을 ArgoCD 배포 알림과 나눴다.** 배포 알림은 머지마다 오므로 섞으면 OOM 알림이 묻힌다.

### 규칙 2개와 그 지표 출처

| 규칙 | 조건 | 지표 출처 |
|---|---|---|
| 파드 재시작 | `increase(restarts_total[10m]) > 0`, `reason` 라벨 동반 | kube-state-metrics |
| 파드 메모리 천장 근접 | `working_set / limit > 0.8`, 1분 지속 | cAdvisor ÷ kube-state-metrics |

**출처가 둘로 갈리는 게 핵심이다.** kube-state-metrics 는 `limit` 만 알고 실사용을 모른다.
kubelet cAdvisor 는 실사용만 알고 `limit` 을 모른다. "천장까지 몇 %" 는 둘을 나눠야 나온다.

배포로는 재시작 알림이 안 울린다 — 롤링 교체는 새 파드를 만들고 `restartCount` 가 0 에서
시작하므로 `increase()` 가 0 이다. 울리는 건 살아 있는 파드가 그 자리에서 다시 뜬 경우뿐이다.

### 알려진 공백

`noDataState: OK` 다. 이 규칙들은 "이상할 때만 데이터가 있는" 형태라 무데이터가 정상이다.
대가로 **kube-state-metrics 가 죽으면 알림이 조용히 침묵한다.** 잡으려면 `up == 0` 규칙이
따로 필요한데 아직 없다 — 16일간 못 봤던 것과 같은 종류의 구멍이라 알고 남긴다.

## 재구축

### 맥미니

```bash
brew install colima docker docker-compose
colima start --cpu 6 --memory 10 --disk 100 --vm-type vz --mount-type virtiofs
brew services start colima

mkdir -p ~/monitoring && cp -r macmini/* ~/monitoring/
cd ~/monitoring && docker compose up -d
```

**Git 에 없는 파일 2개를 먼저 만들어야 한다.** 없으면 docker 가 그 자리에 디렉토리를
만들어 버리고 Prometheus·Grafana 가 조용히 반쯤 동작한다.

```bash
# 1) kubelet cAdvisor 스크랩용 토큰 (만료 없음 — `kubectl create token` 은 만료가 있어 못 쓴다)
kubectl -n nar apply -f - <<'EOF'
apiVersion: v1
kind: Secret
metadata:
  name: prometheus-kubelet-token
  namespace: nar
  annotations:
    kubernetes.io/service-account.name: prometheus-kubelet
type: kubernetes.io/service-account-token
EOF
kubectl -n nar get secret prometheus-kubelet-token \
  -o jsonpath='{.data.token}' | base64 -d > ~/monitoring/kubelet-token
chmod 600 ~/monitoring/kubelet-token

# 2) 디스코드 컨택포인트
cp ~/monitoring/grafana/provisioning/alerting/contact-points.yaml.example \
   ~/monitoring/grafana/provisioning/alerting/contact-points.yaml
# <WEBHOOK_URL> 을 실제 값으로 치환

docker restart prometheus grafana
```

`ServiceAccount`·`ClusterRole` 쪽은 ArgoCD 가 관리한다
(`infra/k8s/prometheus-kubelet-reader.yaml`, `infra/k8s/kube-state-metrics.yaml`).
**토큰 Secret 만 손으로 만든다** — `infra/k8s` 는 `prune: true` 라, 컨트롤러가 채워 넣는
`data` 와 Git 의 빈 `data` 가 계속 부딪힌다.

대시보드는 Grafana API로 넣는다. 4701(JVM Micrometer), 12900(SpringBoot APM)은 import API로
그대로 들어가지만, **14057(MySQL)은 `__inputs`가 비어 있고 `id`가 박혀 있어 import API가 거부한다.**
`id`를 `null`로 바꿔 `/api/dashboards/db`에 POST해야 들어간다.

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
- mysqld_exporter 비밀번호 — Oracle VM 의 `/etc/mysqld_exporter/.my.cnf` (0600)
- 디스코드 인프라 웹훅 — 맥미니 `~/monitoring/grafana/provisioning/alerting/contact-points.yaml`
- kubelet 스크랩 토큰 — 맥미니 `~/monitoring/kubelet-token` (0600)

뒤의 둘은 `.gitignore` 에 있고 저장소엔 `.example` 만 둔다. 만드는 절차는 「재구축」에 있다.

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
