#!/usr/bin/env bash
# 맥미니 MySQL 논리 백업. 춘천(nargg-1)의 /usr/local/bin/nar-db-backup.sh 를 macOS 로 옮긴 것이다.
#
# 옮기면서 달라진 곳:
#   - stat -c%s (GNU) → stat -f%z (BSD)
#   - mysqldump 경로가 brew 아래다. cron 은 .zshenv 도 안 읽으므로 절대경로로 부른다.
#   - 백업 파일명에 -macmini- 를 넣는다. 컷오버 전까지 춘천 백업과 같은 버킷에 들어가는데,
#     이름이 같으면 나중에 올라간 쪽이 덮어써서 한쪽 백업이 조용히 사라진다.
#   - 인증: brew MySQL 은 로컬 root 가 비밀번호 없이 붙는다(춘천은 auth_socket). 결과는 같다.
#
# 컷오버 전에는 복제본을 뜬다. 복제본은 원본과 동일하므로 백업으로서 유효하고,
# --single-transaction 이라 복제를 멈추지 않는다.
set -euo pipefail
PUSHGW=http://127.0.0.1:9091; CRON_JOB=db-backup; CRON_INSTANCE=macmini

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


MYSQLDUMP=/opt/homebrew/opt/mysql@8.4/bin/mysqldump
DB_NAME=nardb
DEST="$HOME/nar/backups"
KEEP_DAYS=7
MIN_BYTES=5000000          # 정상 덤프는 110MB 내외. 인증 실패·빈 응답 가드.
PAR_FILE="$HOME/nar/.backup-par"
WEBHOOK_FILE="$HOME/nar/.discord-webhook"
LOG="$HOME/nar/backup.log"

log() { echo "[$(date '+%F %T %Z')] $*" >> "$LOG"; }

fail() {
	log "실패: $1"
	if [ -r "$WEBHOOK_FILE" ]; then
		curl -sS -m 15 -X POST -H 'Content-Type: application/json' \
			-d "{\"content\":\"🔴 DB 백업 실패 (맥미니): $1\"}" \
			"$(cat "$WEBHOOK_FILE")" >/dev/null 2>&1 || true
	fi
	push_metric 1
	exit 1
}

mkdir -p "$DEST" && chmod 700 "$DEST"

FILE="$DEST/nar-macmini-$(date +%F).sql.gz"
log "시작 → $FILE"

# --single-transaction: InnoDB 를 락 없이 일관 스냅샷으로(복제도 안 멈춘다)
# --quick: 결과를 메모리에 쌓지 않음
# --no-tablespaces: PROCESS 권한 불필요
# --set-gtid-purged=OFF: 복원 시 GTID 구문이 걸리지 않게
"$MYSQLDUMP" -u root --single-transaction --quick --routines --events \
	--no-tablespaces --set-gtid-purged=OFF \
	--default-character-set=utf8mb4 \
	"$DB_NAME" 2>>"$LOG" | gzip -6 > "$FILE" || { rm -f "$FILE"; fail "mysqldump 오류"; }

SIZE=$(stat -f%z "$FILE")
[ "$SIZE" -ge "$MIN_BYTES" ] || { rm -f "$FILE"; fail "덤프가 너무 작다(${SIZE}바이트)"; }
gunzip -c "$FILE" | tail -1 | grep -q "Dump completed" || { rm -f "$FILE"; fail "덤프가 잘렸다(완료 주석 없음)"; }
log "덤프 완료 ${SIZE}바이트"

if [ -r "$PAR_FILE" ]; then
	PAR="$(cat "$PAR_FILE")"
	BODY=$(mktemp)
	HTTP=$(curl -sS -m 900 -X PUT --data-binary "@${FILE}" \
		-o "$BODY" -w '%{http_code}' \
		"${PAR%/}/$(basename "$FILE")" 2>>"$LOG") || { rm -f "$BODY"; fail "업로드 요청 실패(네트워크)"; }
	case "$HTTP" in
		200|201) log "업로드 완료 $(basename "$FILE") (HTTP $HTTP)" ;;
		*) log "응답본문: $(head -c 300 "$BODY")"; rm -f "$BODY"; fail "업로드 HTTP $HTTP" ;;
	esac
	rm -f "$BODY"
else
	log "PAR 파일 없음 — 로컬 보관만"
fi

# 로컬은 7일치. 오프사이트 보관은 버킷 라이프사이클(30일)이 담당한다.
find "$DEST" -name 'nar-macmini-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
log "종료 (로컬 보관 $(ls -1 "$DEST"/nar-macmini-*.sql.gz 2>/dev/null | wc -l | tr -d ' ')개)"
push_metric 0
