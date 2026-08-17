#!/usr/bin/env bash
# 맥미니 MySQL 복제 상태 감시.
#
# 복제가 조용히 끊기면 세 가지가 한꺼번에 망가진다:
#   1. 그림자 앱이 낡은 데이터를 읽는다
#   2. 04:25 백업이 "틀린 데이터의 정확한 사본"이 된다
#   3. 컷오버 때 그 시점 이후 데이터가 통째로 사라진다
# 그런데 아무 증상도 안 보인다. 그래서 능동적으로 확인한다.
#
# ponytail: Grafana 알림 대신 스크립트를 쓴다. 지표는 이미 Prometheus 에 있어
#   대시보드로 볼 수 있고, 알림만 필요한데 Grafana 알림 프로비저닝은 설정이 장황하다.
#   무음화·에스컬레이션이 필요해지면 그때 Grafana 알림으로 올린다.
set -u

M=/opt/homebrew/opt/mysql@8.4/bin/mysql
WEBHOOK_FILE="$HOME/nar/.discord-webhook"
STATE="$HOME/nar/.replication-state"   # ok | bad
LAG_LIMIT=300                          # 초. 평소 0 이라 5분이면 명백한 이상이다.

notify() {
	[ -r "$WEBHOOK_FILE" ] || return 0
	curl -fsS -m 10 -X POST -H 'Content-Type: application/json' \
		-d "$(printf '{"content":"%s"}' "$1")" "$(cat "$WEBHOOK_FILE")" > /dev/null || true
}

prev=$(cat "$STATE" 2>/dev/null || echo ok)

STATUS=$("$M" -u root -e "SHOW REPLICA STATUS\G" 2>/dev/null)

if [ -z "$STATUS" ]; then
	problem="복제가 아예 설정돼 있지 않다 (SHOW REPLICA STATUS 가 비었다)"
else
	io=$(echo "$STATUS"  | awk -F': ' '/Replica_IO_Running:/{print $2}'  | tr -d ' ')
	sql=$(echo "$STATUS" | awk -F': ' '/Replica_SQL_Running:/{print $2}' | tr -d ' ')
	lag=$(echo "$STATUS" | awk -F': ' '/Seconds_Behind_Source:/{print $2}' | tr -d ' ')
	err=$(echo "$STATUS" | awk -F': ' '/Last_Error:/{print $2}' | head -1)

	problem=""
	[ "$io" = "Yes" ]  || problem="IO 스레드 정지 (${err:-사유없음})"
	[ "$sql" = "Yes" ] || problem="SQL 스레드 정지 (${err:-사유없음})"
	if [ -z "$problem" ] && [ "$lag" != "NULL" ] && [ -n "$lag" ] && [ "$lag" -gt "$LAG_LIMIT" ] 2>/dev/null; then
		problem="복제 지연 ${lag}초 (기준 ${LAG_LIMIT}초)"
	fi
fi

if [ -n "$problem" ]; then
	# 상태가 바뀔 때만 알린다. 계속 깨져 있어도 5분마다 도배하지 않는다.
	[ "$prev" = "bad" ] || notify "🔴 맥미니 MySQL 복제 이상 — ${problem}"
	echo bad > "$STATE"
else
	[ "$prev" = "ok" ] || notify "🟢 맥미니 MySQL 복제 정상 복구"
	echo ok > "$STATE"
fi
