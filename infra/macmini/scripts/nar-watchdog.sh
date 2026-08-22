#!/bin/bash
# NAR 외부 감시자
#
# 앱을 집(맥미니)으로 옮기면 Prometheus·Loki·Grafana 가 전부 같은 기계에 있어서
# 정전이나 회선 단절이 나면 감시자도 같이 죽는다. 그래서 집 밖에 있는 이 박스가
# api.nar.kr 을 바깥에서 찔러본다. 감시자는 감시 대상과 같은 곳에 있으면 안 된다.
set -u
PUSHGW=http://100.111.167.92:9091; CRON_JOB=watchdog; CRON_INSTANCE=nargg

# ── 크론 감시 지표 (Pushgateway → Prometheus → Grafana "크론잡" 대시보드) ──
# 실패해도 잡은 계속 돈다(|| true). 진짜 신호는 "마지막 성공이 오래됐다"라서
# 이 push 자체가 죽으면 대시보드 신선도가 알아서 붉어진다.
_CRON_T0=$(date +%s)
push_metric() { # $1: exit code (0/1)
	local now dur; now=$(date +%s); dur=$((now - _CRON_T0))
	{ printf 'nar_cron_last_run_timestamp_seconds %s\n' "$now"
	  [ "$1" = 0 ] && printf 'nar_cron_last_success_timestamp_seconds %s\n' "$now"
	  printf 'nar_cron_duration_seconds %s\n' "$dur"
	  printf 'nar_cron_exit_code %s\n' "$1"
	} | curl -fsS -m 5 --data-binary @- \
		"${PUSHGW}/metrics/job/${CRON_JOB}/instance/${CRON_INSTANCE}" >/dev/null 2>&1 || true
}


# /v3/api-docs 와 /actuator/health 는 공개 도메인에서 막혀 있다 — 지금은 Traefik 미들웨어가
# 각각 Basic Auth·403 을 건다(옛 nginx 시절과 이유는 같다). 밖에서 쓸 수 있으면서 DB 까지
# 타는 가벼운 엔드포인트를 쓴다.
URL="${NAR_WATCHDOG_URL:-https://api.nar.kr/api/worlds/recent}"
# 웹훅은 저장소에 두지 않는다. 맥미니의 ~/nar/.discord-webhook 과 같은 규칙이다.
WEBHOOK_FILE="${NAR_WATCHDOG_WEBHOOK_FILE:-$HOME/.nar-webhook}"
WEBHOOK="${NAR_WATCHDOG_WEBHOOK:-$( [ -r "$WEBHOOK_FILE" ] && cat "$WEBHOOK_FILE" )}"
STATE="${NAR_WATCHDOG_STATE:-/var/tmp/nar-watchdog.state}"   # 연속 실패 횟수를 들고 있는다
THRESHOLD=3                          # 3분 연속 실패해야 알린다 — 순간 오류로 깨우지 않는다

# 포워딩 끊김(2026-08-21 장애)은 여기까지 오지 않는다 — nar-forward-guard.sh 가 맥미니에서
# 30초 안에 되살린다. 이 감시자가 잡는 것은 guard 가 손댈 수 없는 것들이다:
# 정전, 회선 단절, colima 자체 사망, DB 다운, 앱 크래시.

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

# 잡 생존 신호 + api 연속 실패 수. exit_code 는 항상 0 — api 상태는 위 알림과
# nar_watchdog_consecutive_fails 게이지가 말한다.
push_metric 0
printf 'nar_watchdog_consecutive_fails %s\n' "$fails" | curl -fsS -m 5 --data-binary @- \
	"${PUSHGW}/metrics/job/${CRON_JOB}/instance/${CRON_INSTANCE}" >/dev/null 2>&1 || true
