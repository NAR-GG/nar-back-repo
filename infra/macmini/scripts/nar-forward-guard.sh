#!/usr/bin/env bash
# 호스트↔VM 포워딩 감시자 (맥미니)
#
# 2026-08-21 20:25 장애의 대응이다. 그날 api.nar.kr 이 28분 동안 502 였는데,
# 죽은 것은 앱도 k3s 도 traefik 도 아니고 **포워딩을 전부 나르는 ssh mux master
# 프로세스 하나**였다. cloudflared 는 127.0.0.1:30082 에 붙는데 그 리스너가 사라졌다.
#
# lima(colima)는 게스트 포트가 열리고 닫히는 이벤트에만 반응한다. 이미 만들어 둔
# 포워딩이 살아 있는지는 확인하지 않는다. 그날 게스트 쪽 30082 는 계속 열려 있었으니
# lima 는 이벤트를 못 받고 28분 동안 "Time sync" 만 찍었다. 프로세스 감시로도 안 잡힌다
# — limactl hostagent 는 멀쩡히 살아 있었다.
#
# 그래서 밖에서 리스너 자체를 확인한다. 없으면 lima 가 쓰는 것과 같은 방법으로
# 다시 건다(살아 있는 ControlMaster 에 -O forward). VM 재시작이 아니라 무중단이다.
set -u

CP="$HOME/.colima/_lima/colima/ssh.sock"
COLIMA=/opt/homebrew/bin/colima
WEBHOOK_FILE="$HOME/nar/.discord-webhook"

# 바인드 주소:포트.
#   127.0.0.1 — 소비자가 이 기계 안에만 있는 것. cloudflared 도 127.0.0.1 로 붙으므로
#               실트래픽 포트를 LAN 에 열 이유가 없다(lima 는 0.0.0.0 에 걸었다).
#   0.0.0.0   — 노트북에서 Tailscale 로 보는 대시보드. 좁히면 안 보인다.
FORWARDS="
127.0.0.1:30082
127.0.0.1:30443
127.0.0.1:6443
0.0.0.0:3000
0.0.0.0:9090
0.0.0.0:3100
"

# 여기서 만드는 ssh mux master 가 이 한도를 물려받는다. launchd 기본값 256 이
# 그날의 천장이었다 — 커넥션 하나가 master 의 fd 하나이고, 경기 시작 직후 앱 트래픽이
# 15배(5 → 76 req/s)로 뛰면서 넘겼다. kern.maxfilesperproc 이 61440 이라 여유는 충분하다.
ulimit -n 16384 2>/dev/null || true

log() { printf '%s %s\n' "$(date '+%F %T')" "$1"; }

notify() {
	[ -r "$WEBHOOK_FILE" ] || return 0
	curl -fsS -m 10 -X POST -H 'Content-Type: application/json' \
		-d "$(printf '{"content":"%s"}' "$1")" "$(cat "$WEBHOOK_FILE")" >/dev/null 2>&1 || true
}

master_alive() { ssh -O check -o ControlPath="$CP" dummy >/dev/null 2>&1; }

# master 가 없으면 -O forward 자체가 통하지 않는다. colima 로 한 번 접속해 새로 만든다
# (ControlMaster=auto 라 붙는 순간 생긴다). VM 을 건드리지 않는다.
ensure_master() {
	master_alive && return 0
	# fd 한도를 같이 남긴다. 새 master 는 이 쉘의 한도를 물려받으므로, 256 이 찍히면
	# 위의 ulimit 이 먹지 않은 것이고 같은 장애가 재발할 수 있다는 뜻이다.
	log "ControlMaster 없음 — 재생성 시도 (물려줄 fd 한도 $(ulimit -n))"
	"$COLIMA" ssh -- true >/dev/null 2>&1
	master_alive
}

repaired=""
for f in $FORWARDS; do
	addr=${f%:*}
	port=${f##*:}

	# 0.0.0.0 리스너도 127.0.0.1 로 붙으므로 확인은 항상 루프백으로 한다.
	nc -z -G 2 -w 2 127.0.0.1 "$port" >/dev/null 2>&1 && continue

	if ! ensure_master; then
		log "포워딩 $f 복구 실패 — ControlMaster 를 만들 수 없다. colima 자체를 봐야 한다"
		notify "🔴 맥미니 포워딩 복구 실패 — colima ControlMaster 없음 (수동 확인 필요)"
		exit 1
	fi

	if ssh -O forward -o ControlPath="$CP" -L "$addr:$port:127.0.0.1:$port" dummy >/dev/null 2>&1; then
		log "포워딩 $f 재수립"
		repaired="$repaired $f"
	else
		log "포워딩 $f 재수립 실패"
	fi
done

[ -n "$repaired" ] && notify "🟡 맥미니 호스트↔VM 포워딩 재수립:$repaired"

# ponytail: docker.sock(유닉스 소켓)은 목록에서 뺐다. 서비스 경로가 아니고
# `colima ssh -- docker` 로 대체된다. 필요해지면 같은 루프에
# -L "$HOME/.colima/default/docker.sock:/var/run/docker.sock" 로 넣으면 된다.
exit 0
