# 다이어그램 소스

`.mmd` 는 Mermaid 소스. 렌더 방법 세 가지.

- 깃허브: `.mmd` 는 자동 렌더되지 않는다. 문서에 실을 때는 ```mermaid 펜스로 붙인다.
- 로컬 미리보기: VS Code `Markdown Preview Mermaid Support` 또는 `mermaid-cli`.
  ```bash
  npx -y @mermaid-js/mermaid-cli -i docs/diagrams/push-fanout-batching.mmd -o /tmp/out.svg && open /tmp/out.svg
  ```
- 브라우저: <https://mermaid.live> 에 붙여넣기.

파일 첫 줄들의 `%%` 주석이 노드 ↔ 소스 경로 매핑이다. 코드가 바뀌면 여기부터 확인한다.

| 파일 | 내용 |
|---|---|
| `push-fanout-batching.mmd` | 팬아웃 DB 왕복 배치화 전후 (구독자 단위 → 청크 500) |
