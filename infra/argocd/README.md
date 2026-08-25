# ArgoCD 부트스트랩

`infra/k8s` 는 ArgoCD 가 동기화한다. **이 디렉토리는 ArgoCD 가 동기화하지 않는다** —
ArgoCD 자신을 세우는 파일이라 손으로 한 번 적용한다.

## 세우는 순서

```bash
# 1. sealed-secrets (봉인 키 복원이 먼저다 — infra/k8s/README.md 참고)
kubectl apply -f <sealed-secrets controller.yaml>

# 2. ArgoCD
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts -f <argo-cd install.yaml>

# 3. 저장소 자격증명 + Application + 알림 설정
kubectl apply -f infra/argocd/

# 4. 디스코드 배포 알림용 시크릿 (아래 "디스코드 배포 알림" 절)
```

`--server-side` 가 필요하다. 그냥 `apply` 하면 ApplicationSet CRD 가
`metadata.annotations: Too long: may not be more than 262144 bytes` 로 깨진다 —
`last-applied-configuration` 어노테이션이 한도를 넘는다.

## 저장소 접근

읽기 전용 배포키다(`argocd-macmini`). 조직 설정에서 배포키가 꺼져 있으면 먼저 켠다:

```bash
gh api -X PATCH orgs/NAR-GG -f deploy_keys_enabled_for_repositories=true
```

## UI

```
https://100.111.167.92:30443     Tailscale
https://127.0.0.1:30443          맥미니 로컬
```

자체 서명 인증서라 브라우저 경고가 뜬다. 초기 비밀번호는
`kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d`.

**지금은 LAN 에도 열려 있다.** Colima 의 포트 포워딩 기본값이 `0.0.0.0` 이라
NodePort 가 집 네트워크에 노출된다. `~/.colima/default/colima.yaml` 에
`portInterface: 127.0.0.1` 을 넣어 뒀고, **다음 Colima 재시작에 닫힌다.**
닫히고 나면 Tailscale 접근도 끊기므로, 메트릭(9105)과 같은 방식으로
nginx 가 Tailscale IP 에 바인딩해서 `127.0.0.1:30443` 으로 프록시하게 만든다.

## deploy 브랜치 — ArgoCD 가 보는 곳

ArgoCD 는 `main` 이 아니라 **`deploy`** 브랜치를 본다.

`main` 은 PR 필수라 CI 가 이미지 태그를 직접 못 민다. 바이패스용 PAT 을 CI 에 넣는 건
권한이 과해서, 배포 워크플로가 매번 `main` 에서 브랜치를 새로 만들어 이미지 태그
한 줄만 갈아끼우고 강제 푸시한다.

```
main ──(CI: 이미지 태그 한 줄 수정)──> deploy ──> ArgoCD ──> 클러스터
```

`deploy` 는 항상 **`main` + 커밋 1개**다. 두 브랜치가 갈라지지 않는다.
배포가 검증까지 끝나야 이 스텝에 도달하므로, `deploy` 브랜치는 **실제로 뜬 이미지**만
가리킨다.

### 이 브랜치에 손으로 커밋하지 마라

다음 배포에 강제 푸시로 날아간다.

### 히스토리가 안 남는다

매번 강제 푸시라 `deploy` 의 로그는 늘 커밋 하나다. 배포 이력은 ArgoCD 의
**History and Rollback** 에서 본다.

### 롤백은 Git 을 거쳐야 한다

자동 동기화(`selfHeal`)가 켜져 있어서 ArgoCD UI 에서 롤백해도 몇 초 뒤 `deploy`
브랜치 상태로 되돌아온다. 진짜 롤백은 **옛 커밋 SHA 로 재배포**한다
(`gh workflow run "Build and Deploy to 맥미니 홈서버" --ref <SHA>`).

### 무한 루프가 안 나는 이유

배포 워크플로의 `paths-ignore` 에 `infra/**` 가 있다. 매니페스트만 바뀐 커밋은
배포를 다시 트리거하지 않는다.

## 디스코드 배포 알림

새 리비전이 싱크 성공 + Healthy 가 되면 notifications-controller 가 디스코드로
embed 를 쏜다. 설정은 `notifications-cm.yaml`, 구독은 `nar-app.yaml` 의
`notifications.argoproj.io/subscribe.on-deployed.discord` 어노테이션.

웹훅 URL 시크릿은 봉인해 두지 않았다 — 클러스터 안의 `nar-env` 에서 복사해 만든다:

```bash
kubectl -n argocd create secret generic argocd-notifications-secret \
  --from-literal=discord-webhook-url="$(kubectl -n nar get secret nar-env \
    -o jsonpath='{.data.DISCORD_WEBHOOK_URL}' | base64 -d)"
```

다른 채널로 보내려면 그 채널의 웹훅 URL 로 시크릿만 갈아끼우면 된다.
`oncePer: revision` 이라 selfHeal 재싱크로는 중복 발송되지 않는다.

## "Synced" 가 뜻하지 않는 것

ArgoCD 는 **Git 에 선언된 필드만** 소유한다. 손으로 어노테이션을 하나 더 붙여도
드리프트로 잡지 않고 `Synced` 를 유지한다. 3-way merge 라 선언에 없는 필드는
비교 대상이 아니다.

선언된 필드는 제대로 잡는다 — `replicas` 를 손으로 0 으로 내리면 `OutOfSync`
가 되고, 동기화하면 Git 의 1 로 돌아온다(검증함).
