#!/bin/bash
# NAR 외부 감시자
#
# 앱을 집(맥미니)으로 옮기면 Prometheus·Loki·Grafana 가 전부 같은 기계에 있어서
# 정전이나 회선 단절이 나면 감시자도 같이 죽는다. 그래서 집 밖에 있는 이 박스가
# api.nar.kr 을 바깥에서 찔러본다. 감시자는 감시 대상과 같은 곳에 있으면 안 된다.
set -u

# /v3/api-docs 는 공개 도메인에서 nginx Basic Auth 로 막혀 있다(배포 헬스체크는 컨테이너에
# 직접 붙어서 통과한다). 밖에서 쓸 수 있으면서 DB 까지 타는 가벼운 엔드포인트를 쓴다.
URL="${NAR_WATCHDOG_URL:-https://api.nar.kr/api/worlds/recent}"
WEBHOOK="${NAR_WATCHDOG_WEBHOOK:-}"
STATE=/var/tmp/nar-watchdog.state   # 연속 실패 횟수를 들고 있는다
THRESHOLD=3                          # 3분 연속 실패해야 알린다 — 순간 오류로 깨우지 않는다

notify() {
  [ -n "$WEBHOOK" ] || return 0
  curl -fsS -m 10 -X POST -H 'Content-Type: application/json' \
    -d "$(printf '{"content":"%s"}' "$1")" "$WEBHOOK" > /dev/null || true
}

fails=$(cat "$STATE" 2>/dev/null || echo 0)

# 2xx + JSON 배열 시작까지 본다. DB 가 죽으면 여기서 500 이 나므로 앱만 살아 있는 상태도 걸린다.
# 본문 내용(경기 존재 여부)에는 기대지 않는다 — 경기가 없는 시기에 빈 배열이 정상이라 오탐이 된다.
if curl -fsS --max-time 10 "$URL" | grep -q '^\['; then
  if [ "$fails" -ge "$THRESHOLD" ]; then
    notify "🟢 api.nar.kr 복구 (${fails}분 만에)"
  fi
  echo 0 > "$STATE"
else
  fails=$((fails + 1))
  echo "$fails" > "$STATE"
  # 임계치에 닿는 순간 딱 한 번만 알린다. 계속 죽어 있어도 1분마다 도배하지 않는다.
  if [ "$fails" -eq "$THRESHOLD" ]; then
    notify "🔴 api.nar.kr 응답 없음 (${THRESHOLD}분 연속) — 홈서버·회선 확인"
  fi
fi

# ponytail: 상태 파일 하나로 끝낸다. 감시 대상이 여러 개가 되거나 에스컬레이션이
# 필요해지면 그때 blackbox_exporter + Alertmanager 로 올린다.
