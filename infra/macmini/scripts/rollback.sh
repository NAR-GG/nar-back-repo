#!/usr/bin/env bash
# 컷오버 되돌리기: 맥미니 → EC2.
#
#   ./rollback.sh --check   전제 조건만 확인
#   ./rollback.sh --go      실제 롤백
#
# 컷오버는 519ms 였지만 롤백은 그만큼 못 줄인다. EC2 앱을 다시 띄우는 데 20초쯤
# 걸리고, 그 앱이 떠야 춘천 DB 로 쓰기가 돌아가기 때문이다. 쓰기 차단 구간은
# 20~30초로 잡는다. 롤백은 이미 뭔가 잘못된 상황에서 하는 것이라 이 정도는 받아들인다.
#
# 쓰기를 잃지 않는 근거는 역방향 복제다(cutover-reverse-repl.sh). 컷오버 후 맥미니에
# 들어온 쓰기가 춘천에 따라 들어가 있어야 되돌릴 수 있다. 그게 안 걸려 있으면
# 이 스크립트는 --go 를 거부한다 — 되돌리는 순간 그 데이터가 사라지기 때문이다.
set -uo pipefail

MAC="changha@macmini"
MAC_TS="100.111.167.92"
EC2="ubuntu@100.88.94.95"
EC2_PUBLIC="52.78.203.141"
CHUN="ubuntu@168.107.37.215"
CHUN_KEY="$HOME/.ssh/id_ed25519"
MYSQL_MAC="/opt/homebrew/opt/mysql@8.4/bin/mysql"

CTL=(-o ControlMaster=auto -o ControlPath=/tmp/nar-rollback-%r@%h:%p -o ControlPersist=600 -o ConnectTimeout=10)
mac()  { ssh "${CTL[@]}" "$MAC" "$@"; }
ec2()  { ssh "${CTL[@]}" "$EC2" "$@" 2>/dev/null; }
chun() { ssh "${CTL[@]}" -i "$CHUN_KEY" "$CHUN" "$@" 2>/dev/null | grep -v "post-quantum\|store now\|may need to be upgraded\|^\*\*"; }
warm() { mac true; ec2 true; chun true; }

ok()   { echo "  ✅ $1"; }
warn() { echo "  ⚠️  $1"; }
die()  { echo "  ❌ $1"; exit 1; }

MODE="${1:---check}"

########################################
echo "=== 전제 조건 ==="
########################################

# 1. 역방향 복제가 살아 있어야 한다. 이게 없으면 컷오버 이후의 쓰기를 잃는다.
RIO=$(chun  "sudo mysql -e 'SHOW REPLICA STATUS\G'" | awk -F': ' '/Replica_IO_Running:/{print $2}'  | tr -d ' ')
RSQL=$(chun "sudo mysql -e 'SHOW REPLICA STATUS\G'" | awk -F': ' '/Replica_SQL_Running:/{print $2}' | tr -d ' ')
RLAG=$(chun "sudo mysql -e 'SHOW REPLICA STATUS\G'" | awk -F': ' '/Seconds_Behind_Source:/{print $2}' | tr -d ' ')
if [ "$RIO" = "Yes" ] && [ "$RSQL" = "Yes" ]; then
	ok "역방향 복제 정상, 지연 ${RLAG}초"
else
	warn "역방향 복제가 안 돌고 있다 (IO=$RIO SQL=$RSQL)"
	echo "     되돌리면 컷오버 이후 맥미니에 들어온 쓰기가 사라진다."
	[ "$MODE" = "--go" ] && die "역방향 복제 없이는 진행하지 않는다. 데이터를 버릴 각오면 --force-lose-data 로."
fi

# 2. EC2 앱 컨테이너가 남아 있어야 빠르게 되살린다(이미지 재다운로드 불필요).
EC2_APP=$(ec2 "docker ps -a --format '{{.Names}}' | grep '^nar-gg-' | head -1")
[ -n "$EC2_APP" ] || die "EC2 에 앱 컨테이너가 없다 — 배포부터 다시 해야 한다"
ok "EC2 앱 컨테이너 존재: $EC2_APP"

# 3. 춘천이 read_only 여야 정상(컷오버 상태). 아니면 이미 롤백됐거나 뭔가 어긋났다.
CRO=$(chun "sudo mysql -N -e 'SELECT @@read_only'" | tr -d '[:space:]')
[ "$CRO" = "1" ] && ok "춘천 read_only (컷오버 상태 정상)" || warn "춘천이 이미 쓰기 가능 — 이미 롤백된 상태일 수 있다"

if [ "$MODE" != "--go" ]; then
	echo
	echo "확인만 했다. 실제 롤백은 --go 로."
	exit 0
fi

########################################
echo
echo "=== WINDOW — 쓰기 차단 ==="
########################################
warm
W_START=$(date +%s%N)

# 1. 맥미니 쓰기 차단.
mac "$MYSQL_MAC -u root -e 'SET GLOBAL super_read_only=ON; SET GLOBAL read_only=ON;'"

# 2. 춘천이 맥미니를 따라잡을 때까지.
TARGET=$(mac "$MYSQL_MAC -u root -N -e 'SHOW BINARY LOG STATUS'" | awk '{print $1" "$2}')
T_FILE=$(echo "$TARGET" | awk '{print $1}'); T_POS=$(echo "$TARGET" | awk '{print $2}')
for i in $(seq 1 60); do
	CUR=$(chun "sudo mysql -e 'SHOW REPLICA STATUS\G'" | awk -F': ' '/Relay_Source_Log_File:|Exec_Source_Log_Pos:/{print $2}' | tr -d ' ' | paste -sd' ' -)
	C_FILE=$(echo "$CUR" | awk '{print $1}'); C_POS=$(echo "$CUR" | awk '{print $2}')
	[ "$C_FILE" = "$T_FILE" ] && [ "${C_POS:-0}" -ge "${T_POS:-0}" ] && break
	sleep 0.5
	[ "$i" -eq 60 ] && die "춘천이 30초 안에 따라잡지 못했다"
done
ok "춘천 따라잡기 완료"

# 3. 춘천을 주 DB 로 되돌린다.
chun "sudo mysql -e 'STOP REPLICA; RESET REPLICA ALL; SET GLOBAL super_read_only=OFF; SET GLOBAL read_only=OFF;'"

# 4. EC2 앱 기동. stop 만 해뒀으므로 start 로 바로 뜬다(이미지 pull 불필요).
ec2 "docker start $EC2_APP" > /dev/null
for i in $(seq 1 60); do
	ec2 "curl -fsS -m 3 http://127.0.0.1:8080/api/worlds/recent -o /dev/null || curl -fsS -m 3 http://127.0.0.1:8083/api/worlds/recent -o /dev/null" && break
	sleep 2
	[ "$i" -eq 60 ] && die "EC2 앱이 안 뜬다 — 맥미니가 아직 읽기를 처리 중이니 침착하게 원인부터"
done
EC2_PORT=$(ec2 "docker port $EC2_APP 8080/tcp" | cut -d: -f2 | head -1)
ok "EC2 앱 기동 (포트 ${EC2_PORT})"

# 5. EC2 nginx 를 자기 앱으로 되돌린다. 여기서 쓰기가 열린다.
ec2 "printf 'upstream nar_backend {\n    server 127.0.0.1:%s;\n}\n' ${EC2_PORT} | sudo tee /etc/nginx/conf.d/nar-upstream.conf > /dev/null && sudo nginx -t && sudo nginx -s reload"

W_END=$(date +%s%N)
echo "  쓰기 차단 구간: $(( (W_END - W_START) / 1000000 ))ms"

########################################
echo
echo "=== POST ==="
########################################

# 6. 맥미니 앱의 스케줄러를 끈다. EC2 가 켠 채로 떴으므로 중복을 막아야 한다.
CUR_NAME=$(mac "docker ps --format '{{.Names}}' | grep '^nar-gg-' | head -1")
if [ -n "$CUR_NAME" ]; then
	mac "docker stop -t 20 $CUR_NAME" > /dev/null && ok "맥미니 앱 정지 — 스케줄러 중복 방지"
fi

# 7. 맥미니를 다시 춘천의 복제본으로. 다음 시도를 위해 상태를 원위치시킨다.
mac "$MYSQL_MAC -u root -e 'RESET REPLICA ALL;'" 2>/dev/null
warn "맥미니 복제 재설정은 손으로 해야 한다 (덤프 시점부터 다시 걸거나 좌표를 잡아서)"

# 8. 저장소 변수 되돌리기 — 안 하면 다음 배포가 스케줄러를 켠 채로 맥미니에 뜬다.
if command -v gh > /dev/null; then
	gh variable set APP_SCHEDULING_ENABLED --body false > /dev/null && \
	gh variable set SPRING_FLYWAY_ENABLED  --body false > /dev/null && ok "저장소 변수 false 로 복귀"
fi

echo
echo "=== 남은 수동 작업 ==="
echo "  DNS: Cloudflare 대시보드에서 api.nar.kr 을 되돌린다"
echo "       현재  CNAME → 터널 (프록시 주황)"
echo "       복원  A ${EC2_PUBLIC}  프록시 끄기(회색)"
echo "       cloudflared 로는 A 레코드 복원이 안 돼 수동이다."
echo
echo "  DNS 를 되돌리기 전까지는 트래픽이 CF → 터널 → 맥미니 nginx 로 들어온다."
echo "  맥미니 nginx upstream 을 EC2 로 돌려두면 그동안도 서비스가 된다:"
echo "    ssh $MAC \"printf 'upstream nar_backend {\\\\n    server ${EC2}:8080;\\\\n}\\\\n' > /opt/homebrew/etc/nginx/servers/nar-upstream.conf && nginx -s reload\""
echo
echo "=== 검증 ==="
sleep 5
printf "  api.nar.kr  "; curl -s -o /dev/null -w "HTTP %{http_code}  %{time_total}s\n" --max-time 15 https://api.nar.kr/api/worlds/recent
chun "sudo mysql -N -e 'SELECT CONCAT(\"  춘천 read_only=\", @@read_only)'"
