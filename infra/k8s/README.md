# k3s 매니페스트

맥미니에서 도는 k3s 클러스터의 선언이다. `infra/macmini`(설정 원본 보관소)와 달리
**여기는 실제로 적용되는 소스**다 — ArgoCD 가 이 디렉토리를 보고 클러스터를 맞춘다.

## 지금 상태 (2026-08-22)

**프로덕션 전부가 여기다.** docker 판은 은퇴했다.

```
사용자 ──> Cloudflare ──> cloudflared(파드) ──> Traefik ──> nar-web 파드 ─┐
                                                                          │
                          nar-scheduler 파드 ────────────> 맥미니 MySQL :3306
```

터널까지 클러스터 안이라 **요청 경로가 호스트↔VM 경계를 안 건넌다**
(`cloudflared.yaml` 주석에 이유가 있다). 남은 경계 통과는 DB 접속과 kubectl 뿐이다.

k3s 는 Colima VM **안에** 직접 설치했다(`colima --kubernetes` 아님).
`~/.colima/default/colima.yaml` 의 `kubernetes.enabled: false` 를 유지해야 Colima 가
자기 버전을 덮어쓰지 않는다.

## 파일

| 파일 | 내용 |
|---|---|
| `namespace.yaml` | 네임스페이스 `nar` |
| `nar-env-sealed.yaml` | 앱 환경변수 50개. **봉인돼 있어 저장소에 있어도 안전하다** |
| `nar-files-sealed.yaml` | FCM·APNs·구글 드라이브 자격증명 파일 |
| `docs-auth-sealed.yaml` | api-docs Basic Auth htpasswd |
| `nar-web.yaml` | 웹 Deployment |
| `nar-web-service.yaml` | 웹 Service (NodePort 30081 — Prometheus 가 여기를 긁는다) |
| `nar-scheduler.yaml` | 스케줄러 Deployment. **언제나 정확히 하나** |
| `traefik-routes.yaml` | 라우팅·인증·차단 (옛 nginx `nar.conf` 이식본) |
| `cloudflared.yaml` | Cloudflare 터널. ingress 규칙이 ConfigMap 에 있다 |
| `cloudflared-sealed.yaml` | 터널 자격증명 |

## 시크릿 — sealed-secrets

평문 Secret 은 base64 일 뿐이라 저장소에 못 넣는다. sealed-secrets 컨트롤러의
공개키로 암호화해서 넣는다. **복호화 키는 클러스터 안에만 있어서**, 저장소를 통째로
가져가도 값을 못 읽는다.

```bash
# 값 하나 바꾸기 — 평문은 디스크에 남기지 않는다
kubectl -n nar get secret nar-env -o yaml \
  | kubeseal --cert ~/.nar/sealed-secrets.crt --format yaml --scope namespace-wide \
  > infra/k8s/nar-env-sealed.yaml
```

### 봉인 키를 잃으면 저장소의 봉인본은 영영 못 푼다

두 곳에 백업해 뒀다. 기계를 새로 세울 때는 **k3s 설치 직후, sealed-secrets 설치 전에**
이 키를 먼저 복원해야 한다. 순서를 바꾸면 컨트롤러가 새 키를 만들어 버린다.

| 위치 | 경로 |
|---|---|
| 노트북 | `~/.nar/sealed-secrets-key.yaml` (0600) |
| OCI 버킷 | 백업 PAR 아래 `sealed-secrets-key.yaml` |

```bash
kubectl apply -f sealed-secrets-key.yaml       # 먼저
kubectl apply -f <controller.yaml>              # 그 다음
```

### 기존 Secret 이 있으면 컨트롤러가 덮지 않는다

`Resource "nar-env" already exists and is not managed by SealedSecret` 로 조용히
멈춘다. **SealedSecret 은 `Synced=False` 인데 Secret 은 멀쩡히 있어서, 겉보기엔 정상이다.**
손으로 만든 Secret 을 지우면 컨트롤러가 자기 소유로 다시 만든다.

```bash
kubectl -n nar get secret nar-env -o jsonpath='{.metadata.ownerReferences[0].kind}'
# SealedSecret 이 나와야 정상. 비어 있으면 컨트롤러가 관리하지 않는 것이다.
```

## 앱스토어 알림 부트스트랩

심사·배포는 애플 웹훅으로 받고(`/api/webhooks/appstore`), 고객 리뷰는 폴링으로 당겨온다.
**리뷰가 폴링인 건 우리 선택이 아니다** — 애플 웹훅 이벤트 5종에 리뷰/평점이 없다.

### 1. App Store Connect 에서 키 발급 (Admin 권한 필요)

사용자 및 접근 → 통합 → App Store Connect API 에서 키를 만든다. **APNs 키로는 안 된다** —
같은 `.p8` 모양이지만 발급처가 다르고 JWT `aud` 도 다르다. 받은 값 셋: Issuer ID, Key ID, `.p8` 파일.

### 2. 시크릿 봉인

```bash
# 웹훅 HMAC 시크릿은 우리가 정한다 — 애플 포털에 같은 값을 넣는다
openssl rand -hex 32

kubectl -n nar get secret nar-env -o yaml \
  | kubeseal --cert ~/.nar/sealed-secrets.crt --format yaml --scope namespace-wide \
  > infra/k8s/nar-env-sealed.yaml
```

넣을 키 여섯 개. 하나라도 비면 해당 경로가 조용히 꺼진다(반쯤 채운 설정으로 401 을 반복하지 않는다).

| 키 | 값 |
|---|---|
| `DISCORD_APPSTORE_DEPLOY_WEBHOOK_URL` | 심사·배포 채널. 비우면 운영 웹훅으로 폴백 |
| `DISCORD_APPSTORE_REVIEW_WEBHOOK_URL` | 리뷰 채널. 배포와 나눈다 — 빌드 상태가 잦아 리뷰가 묻힌다 |
| `APP_STORE_APP_ID` | App Store Connect 앱 URL 의 `/apps/<여기>` 숫자 |
| `APP_STORE_WEBHOOK_SECRET` | 위 `openssl rand` 값 |
| `APP_STORE_CONNECT_KEY_BASE64` | `base64 -i AuthKey_XXX.p8` |
| `APP_STORE_CONNECT_KEY_ID` | 통합 페이지의 Key ID |
| `APP_STORE_CONNECT_ISSUER_ID` | 통합 페이지 상단 Issuer ID (Key ID 와 다른 값) |

### 3. 애플 포털에 웹훅 등록

사용자 및 접근 → 통합 → 웹훅 → 이름 / URL `https://api.nar.kr/api/webhooks/appstore` / 시크릿(2번 값).
이벤트는 원하는 것만 골라도 된다 — 릴레이가 `attributes` 를 그대로 펼치므로 종류를 안 가린다.

`/api/webhooks/**` 는 `SecurityConfig` 의 `/api/**` permitAll 에 걸려 **인증 없이 열려 있다.**
HMAC 검증이 유일한 관문이라, 시크릿이 비면 엔드포인트가 `503` 으로 닫힌다(무검증 통과 금지).
traefik 은 `Host(api.nar.kr)` 캐치올로 nar-web 에 보내므로 라우팅 변경은 없다.

### 4. 리뷰 폴링 켜기

`nar-scheduler.yaml` 의 `APP_STORE_REVIEW_MONITOR_ENABLED`. **2026-09-01 에 이미 `"true"`**
(키 4H45HYAFAQ, appId 6786755741 으로 실호출 검증 후 켰다).

**첫 폴링은 씨딩만 하고 발송하지 않는다** — 안 그러면 과거 리뷰 50건이 한꺼번에 쏟아진다.
채널에 뭐가 오려면 두 번째 폴링(기본 30분 뒤)이 필요하다. 로그로 확인:

```bash
kubectl -n nar logs deploy/nar-scheduler | grep 앱스토어
# "첫 가동 — N건 씨딩만" → 다음 주기부터 신규만 발송
```

## 파드가 둘이면 인메모리 상태도 둘이다

`#442` 로 스케줄러를 뗀 뒤로 **`@Scheduled` 는 `nar-scheduler` 에서만 돈다**
(`SchedulerConfig` 가 `app.scheduling.enabled` 로 등록을 좌우하고, `nar-web` 은 `false`).
그래서 폴링이 채우는 인메모리 상태는 **스케줄러 파드에만 있다.**

| 상태 | 사는 곳 | 웹에서는 |
|---|---|---|
| Caffeine 캐시 | 두 파드 각각 | evict 를 부르는 쪽이 전부 스케줄러라 웹 캐시는 아무도 안 지운다 → **TTL 로 해결됨** (`CacheConfigTest` 가 잠근다) |
| `LiveStateStore` (`activeGames`·`latestStates`·`finishedGameIds`) | 스케줄러만 | **영구히 빈 상태.** 사용자 트래픽은 전부 웹으로 오는데(`traefik-routes.yaml`) 웹의 store 는 채워지지 않는다 |
| `LivePollingScheduler.startNotifiedGameIds` | 스케줄러만 | 웹에는 의미 없음 |

`LiveStateStore` 를 읽는 코드가 웹 요청 경로에 있으면 **DB 폴백이 있는지 확인해야 한다.**
`LiveStateQueryService` 는 폴백이 있어 동작하고, `LiveActivityCatchUpService` 는 없어서
#442 이후 무동작이다. 자세한 내용과 실측은 [ADR 0002](../../docs/adr/0002-scheduler-pod-split.md).

**파드를 또 쪼갤 때는 인메모리 상태를 먼저 센다.** 필드가 `ConcurrentHashMap`·`newKeySet`
인 스프링 빈을 찾고, 그 빈을 읽는 코드가 웹 요청 경로에 있는지 본다.

## 리소스 값의 근거

`2026-08-23` 롤아웃에서 OOMKilled 2건이 나 실측 기준으로 다시 잡았다(#461).
그 전 값(`-Xmx2g`, `limits 3Gi`)은 docker 판을 그대로 베낀 근거 없는 숫자였다.

- `-Xmx1024m` — Prometheus 7일 실측: 풀GC 후 생존 힙 **453 MiB**, 힙 커밋 최대 1424 MiB
  (2g 라 G1 이 GC 대신 힙을 늘렸다). 1024m 은 생존의 2.3배다.
  **1.5g·1.7g 로 낮추는 건 무의미하다** — 관측 최대 커밋 1424 MiB 가 그 아래라 캡이 안 걸린다.
- `limits.memory: 2Gi` — RSS 구성식(실측 검산 1583Mi = 힙커밋 1041 + 논힙 339 + 기타 203),
  worst-case 1566Mi 에 모델 오차분을 얹었다. 옛 3Gi 대비 천장이 1/3 낮아져 서지(2파드) 피크가 줄었다.
- OOM 은 cgroup 한도 초과가 아니라 **VM 전역 고갈**이었다(`dmesg: constraint=CONSTRAINT_NONE`).
  그래서 한도를 만지는 게 아니라 실사용량 자체를 줄이는 게 고침이었다.
- 웹은 **CPU 한도 없음** — 한도는 스로틀링이라 여유가 있어도 GC 와 기동이 느려진다.
  요청값을 200m 에서 2 로 올려도 응답시간 차이가 없어서(47ms → 45ms) 500m 로 뒀다.
  스케줄러는 반대로 한도를 둔다 — CSV 인제스트가 웹 파드를 굶기면 안 된다.

## 파드가 docker 보다 6ms 느린 이유

같은 조건(힙 2g, 300회 워밍업)에서 `/api/matches` 가 파드 31ms, docker 25ms 다.
DB 왕복이 파드에서 49µs 더 걸리는데 이 API 가 **요청당 쿼리를 64번** 날려서
49µs × 64 ≈ 3ms 로 증폭된다. 나머지는 측정 오차 범위다.

k3s 문제가 아니라 N+1 문제다. 쿼리를 줄이면 이 차이도 같이 사라진다.
