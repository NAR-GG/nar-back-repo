#!/usr/bin/env bash
# nar 네임스페이스 로그를 하루 단위로 gzip 해 OCI 버킷(logs/)과 로컬에 보관한다.
#
# 왜 필요한가: Loki 보존이 14일(retention_period 336h)이라 그 전 로그는 사라진다.
# 광고 소개서의 일별 순사용자·조회수, 사고 사후 조사에 쓸 원본을 파일로 남긴다.
# 꺼내 쓰기: zcat nar-logs-2026-10-04.log.gz | grep screen_view
#
# 사용: nar-log-archive.sh            # 어제(KST) 하루치
#       nar-log-archive.sh 2026-10-01 # 지정한 날(백필)
set -euo pipefail
PUSHGW=http://127.0.0.1:9091; CRON_JOB=log-archive; CRON_INSTANCE=macmini
LOKI=http://127.0.0.1:3100
DEST="$HOME/nar/logs"
KEEP_DAYS=90
MIN_LINES=1000             # 하루치가 이보다 적으면 Loki 장애·라벨 변경으로 본다.
PAR_FILE="$HOME/nar/.backup-par"
WEBHOOK_FILE="$HOME/nar/.discord-webhook"
LOG="$HOME/nar/log-archive.log"

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
log() { echo "[$(date '+%F %T %Z')] $*" >> "$LOG"; }
fail() {
	log "실패: $1"
	if [ -r "$WEBHOOK_FILE" ]; then
		curl -sS -m 15 -X POST -H 'Content-Type: application/json' \
			-d "{\"content\":\"🔴 로그 아카이브 실패 (맥미니): $1\"}" \
			"$(cat "$WEBHOOK_FILE")" >/dev/null 2>&1 || true
	fi
	push_metric 1
	exit 1
}

DAY="${1:-$(TZ=Asia/Seoul date -v-1d +%F)}"
[[ "$DAY" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || fail "날짜 형식 오류: $DAY"
mkdir -p "$DEST" && chmod 700 "$DEST"
FILE="$DEST/nar-logs-$DAY.log.gz"
TMP="$FILE.part"
log "시작 $DAY → $FILE"

# Loki 는 한 번에 5000줄까지만 준다. 5분 창으로 긁고, 5000줄에 닿은 창은 반으로 쪼갠다.
# 정렬된 "시각 파드 본문" 텍스트로 낸다. 줄 수는 stderr 마지막 줄로 알린다.
( DAY="$DAY" LOKI="$LOKI" python3 - 2> "$TMP.err" <<'PY' | gzip -6 > "$TMP"
import os, sys, json, urllib.request, urllib.parse
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

LOKI, LIMIT = os.environ["LOKI"], 5000
day = datetime.strptime(os.environ["DAY"], "%Y-%m-%d").replace(tzinfo=ZoneInfo("Asia/Seoul"))
start_ns = int(day.timestamp()) * 10**9
end_ns = int((day + timedelta(days=1)).timestamp()) * 10**9
STEP = 300 * 10**9

def fetch(s, e):
    q = urllib.parse.urlencode({"query": '{namespace="nar"}', "start": s, "end": e,
                                "limit": LIMIT, "direction": "forward"})
    with urllib.request.urlopen(f"{LOKI}/loki/api/v1/query_range?{q}", timeout=60) as r:
        res = json.load(r)["data"]["result"]
    rows = [(int(ts), st["stream"].get("pod", "-"), line) for st in res for ts, line in st["values"]]
    if len(rows) >= LIMIT and e - s > 1:      # 잘렸을 수 있다 → 쪼갠다
        m = (s + e) // 2
        return fetch(s, m) + fetch(m, e)
    return rows

total = 0
s = start_ns
while s < end_ns:
    e = min(s + STEP, end_ns)
    for ts, pod, line in sorted(fetch(s, e)):
        t = datetime.fromtimestamp(ts / 1e9, timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"
        sys.stdout.write(f"{t} {pod} {line}\n")
        total += 1
    s = e
sys.stderr.write(f"LINES={total}\n")
PY
) || { log "파이썬 오류: $(tail -c 400 "$TMP.err" 2>/dev/null)"; rm -f "$TMP" "$TMP.err"; fail "Loki 조회 오류"; }
N=$(grep -o 'LINES=[0-9]*' "$TMP.err" | tail -1 | cut -d= -f2); rm -f "$TMP.err"
[ "${N:-0}" -ge "$MIN_LINES" ] || { rm -f "$TMP"; fail "$DAY 로그가 너무 적다(${N:-0}줄)"; }
gzip -t "$TMP" || { rm -f "$TMP"; fail "gzip 검증 실패"; }
mv "$TMP" "$FILE"
log "압축 완료 ${N}줄 $(stat -f%z "$FILE")바이트"

if [ -r "$PAR_FILE" ]; then
	PAR="$(cat "$PAR_FILE")"; BODY=$(mktemp)
	HTTP=$(curl -sS -m 900 -X PUT --data-binary "@${FILE}" -o "$BODY" -w '%{http_code}' \
		"${PAR%/}/logs/$(basename "$FILE")" 2>>"$LOG") || { rm -f "$BODY"; fail "업로드 요청 실패(네트워크)"; }
	case "$HTTP" in
		200|201) log "업로드 완료 logs/$(basename "$FILE") (HTTP $HTTP)" ;;
		*) log "응답본문: $(head -c 300 "$BODY")"; rm -f "$BODY"; fail "업로드 HTTP $HTTP" ;;
	esac
	rm -f "$BODY"
else
	log "PAR 파일 없음 — 로컬 보관만"
fi

# 로컬 90일. 오프사이트(logs/)는 버킷 수명주기 90일이 담당한다.
find "$DEST" -name 'nar-logs-*.log.gz' -mtime +"$KEEP_DAYS" -delete
log "종료"
push_metric 0
