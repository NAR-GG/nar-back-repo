# k3s 매니페스트

맥미니에서 도는 k3s 클러스터의 선언이다. `infra/macmini`(설정 원본 보관소)와 달리
**여기는 실제로 적용되는 소스**다 — ArgoCD 가 이 디렉토리를 보고 클러스터를 맞춘다.

## 지금 상태 (2026-08-18)

**프로덕션은 아직 docker 다.** k3s 에 올라간 앱은 트래픽을 안 받는다.

```
사용자 ──> Cloudflare ──> cloudflared ──> nginx :8081 ──> docker 컨테이너 :8083   ← 실서비스
                                                          맥미니 MySQL :3306
                                                              ↑
                                          k3s 파드 nar-web ────┘                  ← 트래픽 없음
```

k3s 는 Colima VM **안에** 직접 설치했다(`colima --kubernetes` 아님).
`~/.colima/default/colima.yaml` 의 `kubernetes.enabled: false` 를 유지해야 Colima 가
자기 버전을 덮어쓰지 않는다.

## 파일

| 파일 | 내용 |
|---|---|
| `namespace.yaml` | 네임스페이스 `nar` |
| `nar-env-sealed.yaml` | 앱 환경변수 50개. **봉인돼 있어 저장소에 있어도 안전하다** |
| `nar-web.yaml` | 웹 Deployment |

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

## 리소스 값의 근거

- `-Xmx2g` — docker 판과 같게 맞췄다. 1g 로 낮추면 응답이 느려진다.
- `limits.memory: 3Gi` — 힙 2g 위에 메타스페이스·스레드 스택·다이렉트 버퍼가 얹힌다.
  **2Gi 로 잡으면 힙이 차는 순간 OOMKill 이다.** 실측 RSS 는 1318Mi(힙 미포화).
- CPU 한도 없음 — 한도는 스로틀링이라 여유가 있어도 GC 와 기동이 느려진다.
  요청값을 200m 에서 2 로 올려도 응답시간 차이가 없어서(47ms → 45ms) 500m 로 뒀다.

## 파드가 docker 보다 6ms 느린 이유

같은 조건(힙 2g, 300회 워밍업)에서 `/api/matches` 가 파드 31ms, docker 25ms 다.
DB 왕복이 파드에서 49µs 더 걸리는데 이 API 가 **요청당 쿼리를 64번** 날려서
49µs × 64 ≈ 3ms 로 증폭된다. 나머지는 측정 오차 범위다.

k3s 문제가 아니라 N+1 문제다. 쿼리를 줄이면 이 차이도 같이 사라진다.
