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

# 컷오버 후 감시 방향이 뒤집혔다. 맥미니는 이제 주 DB 라 SHOW REPLICA STATUS 가 비고,
# 감시해야 할 것은 춘천이 맥미니를 잘 따라오는가(역방향 복제)다.
# 춘천에는 SSH 키가 없어 직접 못 물어보므로, 이미 붙어 있는 mysqld_exporter 지표를
# Prometheus 로 읽는다. 춘천은 MySQL 8.0 이라 지표 이름이 8.4 와 다르다
# (slave_io_running / seconds_behind_master).
PROM="http://localhost:9090/api/v1/query"
M=/opt/homebrew/opt/mysql@8.4/bin/mysql

# launchd 의 PATH 는 /usr/bin:/bin:/usr/sbin:/sbin 뿐이라 brew 의 docker 를 못 찾는다.
# 그러면 지표 조회가 조용히 빈 문자열을 내고, 복제가 멀쩡한데도 "지표가 없다"로
# bad 를 찍는다. 손으로 실행하면 PATH 가 있어서 잘 되기 때문에 알아채기 어렵다.
# 절대경로로 박는다. (2026-08-18 실제로 이 이유로 하루 종일 오탐이었다)
DOCKER=/opt/homebrew/bin/docker

WEBHOOK_FILE="$HOME/nar/.discord-webhook"
STATE="$HOME/nar/.replication-state"   # ok | bad
LAG_LIMIT=300                          # 초. 평소 0 이라 5분이면 명백한 이상이다.

notify() {
	[ -r "$WEBHOOK_FILE" ] || return 0
	curl -fsS -m 10 -X POST -H 'Content-Type: application/json' \
		-d "$(printf '{"content":"%s"}' "$1")" "$(cat "$WEBHOOK_FILE")" > /dev/null || true
}

prev=$(cat "$STATE" 2>/dev/null || echo ok)

q() { "$DOCKER" exec prometheus wget -qO- "${PROM}?query=$1" 2>/dev/null \
	| python3 -c "import json,sys; r=json.load(sys.stdin)['data']['result']; print(r[0]['value'][1] if r else '')" 2>/dev/null; }

# 맥미니가 아직 복제본이면(컷오버 전) 기존 방식, 주 DB 면 역방향을 본다.
STATUS=$("$M" -u root -e "SHOW REPLICA STATUS\G" 2>/dev/null)

if [ -z "$STATUS" ]; then
	# 컷오버 후. 춘천이 맥미니를 따라오는지 지표로 본다.
	rio=$(q mysql_slave_status_slave_io_running)
	rsql=$(q mysql_slave_status_slave_sql_running)
	rlag=$(q mysql_slave_status_seconds_behind_master)
	problem=""
	if [ -z "$rio" ]; then
		problem="역방향 복제 지표가 없다 (춘천 exporter 또는 Prometheus 확인)"
	elif [ "$rio" != "1" ] || [ "$rsql" != "1" ]; then
		problem="역방향 복제 정지 (춘천 io=${rio} sql=${rsql}) — 롤백해도 쓰기를 잃는다"
	elif [ -n "$rlag" ] && [ "${rlag%.*}" -gt "$LAG_LIMIT" ] 2>/dev/null; then
		problem="역방향 복제 지연 ${rlag}초"
	fi
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
