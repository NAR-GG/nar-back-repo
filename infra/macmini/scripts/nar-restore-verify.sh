#!/bin/bash
# NAR 백업 복원 검증기 (춘천 es-vnic, 주 1회 cron)
#
# 매일 04:25 백업(맥미니 → OCI 버킷)은 "덤프가 잘렸나"까지만 본다. 이 스크립트는
# 나머지 반쪽 — **그 덤프가 실제로 복원되는가** — 를 실기계에서 증명한다.
# 백업은 복원해 본 적 없으면 백업이 아니다.
#
# 소스: 맥미니 ~/nar/backups 의 최신 덤프 (버킷 PAR 는 쓰기 전용이라 못 읽는다.
#       같은 파일이 버킷에도 올라가므로 이걸 검증하면 버킷 사본도 검증된 셈이다.)
# 대상: 이 박스 로컬 MySQL 8.0 의 nar_verify 스키마 (프로덕션은 8.4 — 메이저 다운이지만
#       춘천 복제본이 8.0 으로 8.4 를 따라오고 있어 호환은 이미 증명돼 있다)
#
# 실패 기준(🔴): 전송·gzip·복원 SQL 오류, 테이블 수 불일치, 데이터가 36시간보다 낡음
# 성공(🟢): 주 1회 결과 요약 — 침묵이 아니라 성공도 알린다. 크론이 죽은 것과
#           구분이 안 되기 때문이다.
set -u
PUSHGW=http://100.111.167.92:9091; CRON_JOB=restore-verify; CRON_INSTANCE=es-vnic

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


MAC="changha@100.111.167.92"
WORK=/home/ubuntu/restore-verify
WEBHOOK_FILE=/home/ubuntu/.discord-webhook
LOG=$WORK/verify.log

log() { echo "[$(date '+%F %T')] $1" >> "$LOG"; }
notify() {
  curl -fsS -m 10 -X POST -H 'Content-Type: application/json' \
    -d "$(python3 -c 'import json,sys; print(json.dumps({"content": sys.argv[1]}))' "$1")" \
    "$(cat $WEBHOOK_FILE)" >/dev/null 2>&1 || true
}
fail() { log "FAIL: $1"; notify "🔴 백업 복원 검증 실패: $1"; push_metric 1; exit 1; }

mkdir -p "$WORK"
log "=== 시작 ==="

# 1. 최신 덤프 가져오기
LATEST=$(ssh -o ConnectTimeout=15 "$MAC" 'ls ~/nar/backups/nar-macmini-*.sql.gz | tail -1') \
  || fail "맥미니 접속 실패 (Tailscale/키 확인)"
NAME=$(basename "$LATEST")
scp -q "$MAC:$LATEST" "$WORK/$NAME" || fail "덤프 전송 실패: $NAME"
SIZE=$(stat -c%s "$WORK/$NAME")

# 2. 무결성 — gzip 자체 + 완료 주석 (백업 스크립트와 같은 기준을 사본에서 재확인)
gunzip -t "$WORK/$NAME" || fail "gzip 손상: $NAME"
gunzip -c "$WORK/$NAME" | tail -1 | grep -q "Dump completed" || fail "덤프 잘림(완료 주석 없음): $NAME"

# 3. 실복원
sudo mysql -e "DROP DATABASE IF EXISTS nar_verify; CREATE DATABASE nar_verify CHARACTER SET utf8mb4"
T0=$(date +%s)
gunzip -c "$WORK/$NAME" | sudo mysql nar_verify || fail "복원 중 SQL 오류: $NAME"
ELAPSED=$(( $(date +%s) - T0 ))

# 4. 검증 — 구조: 테이블 수가 소스와 같은가
RESTORED_TABLES=$(sudo mysql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='nar_verify'")
SOURCE_TABLES=$(ssh "$MAC" "/opt/homebrew/opt/mysql@8.4/bin/mysql -u root -N -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='nardb' AND table_type='BASE TABLE'\"") \
  || fail "소스 테이블 수 조회 실패"
[ "$RESTORED_TABLES" -eq "$SOURCE_TABLES" ] || fail "테이블 수 불일치: 복원 $RESTORED_TABLES vs 소스 $SOURCE_TABLES"

# 5. 검증 — 신선도: 매분 쌓이는 테이블의 최신 행이 36시간 안쪽인가
#    (덤프가 낡은 파일이면 여기서 걸린다. 04:25 덤프를 다음날 검증해도 30시간 이내)
FRESH_TS=$(sudo mysql -N -e "SELECT MAX(created_at) FROM nar_verify.member_notification")
AGE_H=$(sudo mysql -N -e "SELECT TIMESTAMPDIFF(HOUR, '$FRESH_TS', UTC_TIMESTAMP() + INTERVAL 9 HOUR)")
[ -n "$AGE_H" ] && [ "$AGE_H" -le 36 ] || fail "데이터가 낡음: 최신 알림이 ${AGE_H}시간 전 ($FRESH_TS)"

MEMBERS=$(sudo mysql -N -e "SELECT COUNT(*) FROM nar_verify.member" 2>/dev/null || echo "?")
ROWS_NOTI=$(sudo mysql -N -e "SELECT COUNT(*) FROM nar_verify.member_notification")

# 6. 정리 — 덤프 사본은 남기지 않는다 (디스크 37GB 지만 쌓일 이유가 없다)
rm -f "$WORK"/nar-macmini-*.sql.gz

log "OK: $NAME tables=$RESTORED_TABLES members=$MEMBERS ${ELAPSED}s"
notify "🟢 백업 복원 검증 통과: \`$NAME\` ($(( SIZE / 1024 / 1024 ))MB)
복원 ${ELAPSED}초 · 테이블 ${RESTORED_TABLES}개(소스 일치) · 회원 ${MEMBERS} · 알림 ${ROWS_NOTI}행 · 최신 데이터 ${AGE_H}시간 전"
push_metric 0
