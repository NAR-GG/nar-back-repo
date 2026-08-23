#!/usr/bin/env bash
# Colima VM 메모리 감시.
#
# 파드를 OOM-kill 하는 주체는 macOS 가 아니라 그 안의 VM 이다. 그런데 VM 메모리를 보는 지표가
# 하나도 없었다 — Prometheus 타깃 5개는 앱 actuator 2개, mysqld_exporter 2개, pushgateway 뿐이다.
# nar-web OOM(#461) 때 JVM 힙 지표는 있었는데 OOM-kill 을 결정하는 RSS 를 못 봤던 것도 같은 구멍이다.
#
# VM 이 좁다. 실측 2026-08-23:
#   총 9,921MiB / 사용 4,891 / available 5,030 / Swap 0
#   k3s 파드   nar-web 1,223 · nar-scheduler 1,032 · 그 외 ~700
#   k8s 밖 docker  prometheus 412 · grafana 375 · loki 248 · alloy 92 · 나머지 51  = 1,178
# 마지막 줄이 중요하다 — 이 1,178MiB 는 k8s allocatable 회계에 없다. k8s 는 실제보다 여유가
# 많다고 믿는다. 지금은 replicas 가 전부 1이고 오토스케일이 없어 해가 없지만, 마진을 건드리는
# 변경(RollingUpdate 전환 등)을 할 때 계기가 없으면 언제 한계에 닿는지 알 수 없다.
#
# Swap 이 0이라 초과는 완만한 저하 없이 곧바로 OOM-kill 이다. 그래서 여유가 줄어드는 것을
# 미리 봐야 한다.
#
# ponytail: node-exporter 컨테이너를 넣지 않는다. Grafana 알림이 프로비저닝돼 있지 않아
#   (provisioning 에 alerting/ 이 없다) 지표만 늘고 알람은 안 생긴다 — 지금 없는 게 정확히
#   그 알람이다. 이미 검증된 경로(스크립트 → pushgateway → Grafana 크론 대시보드 + 디스코드)를
#   그대로 쓰고, 지표 종류가 더 필요해지면 그때 node-exporter 를 올린다.
#   nar-replication-check.sh 와 같은 판단이다.
set -u
PUSHGW=http://127.0.0.1:9091; CRON_JOB=vm-memory; CRON_INSTANCE=macmini

_CRON_T0=$(date +%s)
push_metric() { # $1: exit code (0/1), $2: available MiB, $3: total MiB
	local now dur; now=$(date +%s); dur=$((now - _CRON_T0))
	{ printf 'nar_cron_last_run_timestamp_seconds %s\n' "$now"
	  [ "$1" = 0 ] && printf 'nar_cron_last_success_timestamp_seconds %s\n' "$now"
	  printf 'nar_cron_duration_seconds %s\n' "$dur"
	  printf 'nar_cron_exit_code %s\n' "$1"
	  # 대시보드에서 추이를 보는 값. 알람과 별개로 이게 있어야 "언제부터 줄었나"를 되짚을 수 있다.
	  [ -n "${2:-}" ] && printf 'nar_vm_memory_available_bytes %s\n' "$(( $2 * 1024 * 1024 ))"
	  [ -n "${3:-}" ] && printf 'nar_vm_memory_total_bytes %s\n' "$(( $3 * 1024 * 1024 ))"
	} | curl -fsS -m 5 --data-binary @- \
		"${PUSHGW}/metrics/job/${CRON_JOB}/instance/${CRON_INSTANCE}" >/dev/null 2>&1 || true
}

export PATH="$PATH:/opt/homebrew/bin:/usr/local/bin"

WEBHOOK_FILE="$HOME/nar/.discord-webhook"
STATE="$HOME/nar/.vm-memory-state"     # ok | bad
# 읽기 실패 연속 횟수. colima CLI 는 VM 이 바쁘면 느려지거나 한 번씩 실패한다(실측: HOME 이
# 다르면 설정을 못 찾아 즉시 실패). 단발 실패로 알림을 보내면 오탐이 쌓여 알람을 무시하게 된다.
FAILS="$HOME/nar/.vm-memory-read-fails"
READ_FAIL_LIMIT=2
# 여유 기준. 파드 하나가 실측 1.0~1.2GiB 라, 1.5GiB 밑이면 "롤아웃 한 번을 못 버틴다"는 뜻이다.
LIMIT_MIB=1500

notify() {
	[ -r "$WEBHOOK_FILE" ] || return 0
	curl -fsS -m 10 -X POST -H 'Content-Type: application/json' \
		-d "$(printf '{"content":"%s"}' "$1")" "$(cat "$WEBHOOK_FILE")" > /dev/null || true
}

prev=$(cat "$STATE" 2>/dev/null || echo ok)

# free 의 available 을 본다. free 만 보면 안 된다 — buff/cache 는 회수 가능해서, 실측에서도
# free 1,784MiB 인데 available 5,259MiB 였다. free 로 판정하면 상시 오탐이다.
# timeout 을 쓰지 않는다. macOS 에는 GNU coreutils 의 timeout 이 없어서(gtimeout 만 별도 설치)
# 붙이면 이 줄이 통째로 실패하고 "VM 을 읽지 못했다"로 오진한다 — 실측으로 밟았다.
# CLI 가 멈춰도 launchd 는 StartInterval 이 지나도 앞 인스턴스가 살아 있으면 새로 띄우지 않으므로
# 잡이 겹쳐 쌓이지도 않는다.
LINE=$(colima ssh -- free -m 2>/dev/null | awk '/^Mem:/{print $2, $7}')
TOTAL=$(echo "$LINE" | awk '{print $1}')
AVAIL=$(echo "$LINE" | awk '{print $2}')

problem=""
if [ -z "${AVAIL:-}" ]; then
	# VM 이 안 떠 있거나 colima CLI 가 응답하지 않는다. 연속 실패일 때만 사고로 본다.
	fails=$(( $(cat "$FAILS" 2>/dev/null || echo 0) + 1 ))
	echo "$fails" > "$FAILS"
	if [ "$fails" -ge "$READ_FAIL_LIMIT" ]; then
		problem="Colima VM 메모리를 ${fails}회 연속 읽지 못했다 (VM 정지 또는 colima CLI 이상)"
	else
		# 아직 단발이다. 지표만 남기고 상태는 건드리지 않는다.
		push_metric 1 "" ""
		exit 0
	fi
elif [ "$AVAIL" -lt "$LIMIT_MIB" ]; then
	echo 0 > "$FAILS"
	problem="Colima VM 여유 메모리 ${AVAIL}MiB / 총 ${TOTAL}MiB (기준 ${LIMIT_MIB}MiB). Swap 이 없어 초과 시 즉시 OOM-kill 이다"
else
	echo 0 > "$FAILS"
fi

if [ -n "$problem" ]; then
	# 상태가 바뀔 때만 알린다. 계속 낮아도 5분마다 도배하지 않는다.
	[ "$prev" = "bad" ] || notify "🟠 ${problem}"
	echo bad > "$STATE"
	push_metric 1 "${AVAIL:-}" "${TOTAL:-}"
else
	[ "$prev" = "ok" ] || notify "🟢 Colima VM 메모리 여유 회복 (${AVAIL}MiB)"
	echo ok > "$STATE"
	push_metric 0 "$AVAIL" "$TOTAL"
fi
