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

# 3. 저장소 자격증명 + Application
kubectl apply -f infra/argocd/
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

## 자동 동기화를 아직 안 켠 이유

배포 파이프라인이 이미지 태그를 저장소에 쓰지 않는다. 지금 자동 동기화를 켜면
새 이미지를 배포해도 ArgoCD 가 Git 에 적힌 낡은 태그로 되돌린다.
CI 가 태그를 커밋하게 만든 다음 켠다.

## "Synced" 가 뜻하지 않는 것

ArgoCD 는 **Git 에 선언된 필드만** 소유한다. 손으로 어노테이션을 하나 더 붙여도
드리프트로 잡지 않고 `Synced` 를 유지한다. 3-way merge 라 선언에 없는 필드는
비교 대상이 아니다.

선언된 필드는 제대로 잡는다 — `replicas` 를 손으로 0 으로 내리면 `OutOfSync`
가 되고, 동기화하면 Git 의 1 로 돌아온다(검증함).
