#!/usr/bin/env bash
# EC2 → 맥미니 컷오버.
#
# 노트북에서 실행한다(양쪽에 SSH 가 되는 곳). Tailscale 안이면 어디서든 된다.
#
#   ./cutover.sh --check   전제 조건만 확인하고 아무것도 바꾸지 않는다
#   ./cutover.sh --go      실제 컷오버
#
# 쓰기가 막히는 구간은 WINDOW 단계뿐이다. 리허설 실측 519ms:
#   원본잠금 76 / 위치읽기 77 / 따라잡기 96 / STOP+read_only 79 / my.cnf 89 / nginx 98
# 사람이 판단할 게 없도록 전부 스크립트에 넣었다 — 새벽 5시에 손으로 치면
# 그 자체가 다운타임이다. SSH 연결 다중화가 없으면 같은 순서가 6~7초가 된다.
#
# 읽기는 안 끊긴다. DNS 를 먼저 바꾸지 않고, EC2 nginx 의 upstream 을 맥미니로 돌려서
# 캐시된 DNS 로 EC2 에 오는 요청도 맥미니가 처리하게 만든다. DNS 전환은 그 뒤에 한다.
set -uo pipefail

MAC="changha@macmini"
MAC_TS="100.111.167.92"
EC2="ubuntu@100.88.94.95"
CHUN="ubuntu@168.107.37.215"
CHUN_KEY="$HOME/.ssh/id_ed25519"
MYSQL_MAC="/opt/homebrew/opt/mysql@8.4/bin/mysql"
IMAGE_FALLBACK="menten4859/nar-gg:latest"

# SSH 연결 다중화. 이게 없으면 창 시간의 대부분이 SSH 접속 비용이다 —
# 리허설에서 명령당 ~1초가 붙어 창이 6~7초가 됐다. 연결을 재사용하면 ~50ms 로 떨어진다.
# 문자열이 아니라 배열로 둔다. 문자열을 펼치면 -o 인자가 붙어버려
# "keyword controlmaster extra arguments" 경고가 난다.
CTL=(-o ControlMaster=auto -o ControlPath=/tmp/nar-cutover-%r@%h:%p -o ControlPersist=600 -o ConnectTimeout=10)

mac()  { ssh "${CTL[@]}" "$MAC" "$@"; }
ec2()  { ssh "${CTL[@]}" "$EC2" "$@" 2>/dev/null; }
chun() { ssh "${CTL[@]}" -i "$CHUN_KEY" "$CHUN" "$@" 2>/dev/null | grep -v "post-quantum\|store now\|may need to be upgraded\|^\*\*"; }

# 창에 들어가기 전에 세 연결을 미리 세워둔다. 창 안에서 첫 접속이 일어나면 안 된다.
warm_connections() { mac true; ec2 true; chun true; }

ok()   { echo "  ✅ $1"; }
warn() { echo "  ⚠️  $1"; }
die()  { echo "  ❌ $1"; exit 1; }

MODE="${1:---check}"

########################################
echo "=== 전제 조건 ==="
########################################

LAG=$(mac "$MYSQL_MAC -u root -e 'SHOW REPLICA STATUS\G'" | awk -F': ' '/Seconds_Behind_Source:/{print $2}' | tr -d ' ')
IO=$(mac  "$MYSQL_MAC -u root -e 'SHOW REPLICA STATUS\G'" | awk -F': ' '/Replica_IO_Running:/{print $2}'  | tr -d ' ')
SQL=$(mac "$MYSQL_MAC -u root -e 'SHOW REPLICA STATUS\G'" | awk -F': ' '/Replica_SQL_Running:/{print $2}' | tr -d ' ')
[ "$IO" = "Yes" ] && [ "$SQL" = "Yes" ] || die "복제가 안 돌고 있다 (IO=$IO SQL=$SQL)"
[ "${LAG:-999}" -le 5 ] 2>/dev/null || die "복제 지연 ${LAG}초 — 0 에 가까워야 한다"
ok "복제 정상, 지연 ${LAG}초"

mac "curl -fsS -m 5 http://127.0.0.1:8081/api/worlds/recent -o /dev/null" && ok "맥미니 앱 응답" || die "맥미니 앱이 응답하지 않는다"
ec2 "curl -fsS -m 5 http://127.0.0.1:8080/api/worlds/recent -o /dev/null || curl -fsS -m 5 http://127.0.0.1:8083/api/worlds/recent -o /dev/null" \
	&& ok "EC2 앱 응답" || warn "EC2 앱 직접 응답 확인 실패(포트 확인 필요)"

ec2 "curl -fsS -m 8 -H 'Host: api.nar.kr' http://${MAC_TS}:8081/api/worlds/recent -o /dev/null" \
	&& ok "EC2 → 맥미니 nginx 도달 (컷오버 창의 핵심 경로)" || die "EC2 에서 맥미니 nginx 에 못 붙는다"

BK=$(mac "ls -1 ~/nar/backups/nar-macmini-*.sql.gz 2>/dev/null | tail -1")
[ -n "$BK" ] && ok "최근 백업: $(basename "$BK")" || warn "맥미니 백업 파일이 없다"

LIVE=$(mac "$MYSQL_MAC -u root -N -e \"SELECT COUNT(*) FROM nardb.league_match WHERE state='inProgress'\"" 2>/dev/null)
if [ "${LIVE:-0}" -gt 0 ]; then
	warn "진행 중인 경기 ${LIVE}건 — 컷오버를 미루는 게 좋다"
	[ "$MODE" = "--go" ] && die "경기 중에는 진행하지 않는다"
else
	ok "진행 중인 경기 없음"
fi

if [ "$MODE" != "--go" ]; then
	echo
	echo "확인만 했다. 실제 컷오버는 --go 로."
	exit 0
fi

########################################
echo
echo "=== WINDOW — 여기서부터 쓰기가 막힌다 ==="
########################################
warm_connections
W_START=$(date +%s%N)

# 1. 원본 잠금. 이 순간부터 사용자 쓰기가 실패한다.
chun "sudo mysql -e 'SET GLOBAL super_read_only=ON; SET GLOBAL read_only=ON;'"

# 2. 맥미니가 원본의 마지막 위치까지 따라잡을 때까지 기다린다.
#    평소 지연이 0 이라 보통 1초 안이지만, 큰 트랜잭션이 진행 중이면 길어질 수 있다.
TARGET=$(chun "sudo mysql -N -e 'SHOW MASTER STATUS'" | awk '{print $1" "$2}')
T_FILE=$(echo "$TARGET" | awk '{print $1}'); T_POS=$(echo "$TARGET" | awk '{print $2}')
for i in $(seq 1 60); do
	CUR=$(mac "$MYSQL_MAC -u root -e 'SHOW REPLICA STATUS\G'" | awk -F': ' '/Relay_Source_Log_File:|Exec_Source_Log_Pos:/{print $2}' | tr -d ' ' | paste -sd' ' -)
	C_FILE=$(echo "$CUR" | awk '{print $1}'); C_POS=$(echo "$CUR" | awk '{print $2}')
	if [ "$C_FILE" = "$T_FILE" ] && [ "${C_POS:-0}" -ge "${T_POS:-0}" ]; then break; fi
	sleep 0.5
	[ "$i" -eq 60 ] && die "30초 안에 따라잡지 못했다 — 원본 잠금을 풀고 중단해야 한다"
done

# 3. 맥미니를 주 DB 로. 설정 파일과 런타임을 같이 푼다 —
#    한쪽만 하면 재기동 때 되돌아간다.
mac "$MYSQL_MAC -u root -e 'STOP REPLICA; SET GLOBAL super_read_only=OFF; SET GLOBAL read_only=OFF;'"
mac "python3 - <<'PY'
p='/opt/homebrew/etc/my.cnf'
s=open(p).read()
s=s.replace('read_only = ON\n','').replace('super_read_only = ON\n','')
open(p,'w').write(s)
PY"

# 4. EC2 nginx 를 맥미니로 돌린다. 여기가 끝나면 쓰기가 다시 열린다.
ec2 "printf 'upstream nar_backend {\n    server ${MAC_TS}:8081;\n}\n' | sudo tee /etc/nginx/conf.d/nar-upstream.conf > /dev/null && sudo nginx -t && sudo nginx -s reload"

W_END=$(date +%s%N)
echo "  쓰기 차단 구간: $(( (W_END - W_START) / 1000000 ))ms"

########################################
echo
echo "=== POST — 사용자 영향 없는 구간 ==="
########################################

# 5. EC2 앱 정지. 스케줄러가 여기서 끝난다.
OLD=$(ec2 "docker ps --format '{{.Names}}' | grep '^nar-gg-' | head -1")
ec2 "docker stop -t 30 $OLD" > /dev/null && ok "EC2 앱 정지 ($OLD) — 스케줄러 종료"

# 6. 맥미니에 스케줄러 켠 인스턴스를 띄운다. 블루-그린이라 읽기는 안 끊긴다.
#    배포 워크플로를 쓰면 빌드까지 3~4분이라 스케줄러 공백이 길어져서, 여기서는
#    지금 돌고 있는 이미지를 그대로 재사용한다. 이후 배포는 워크플로가 맡는다.
CUR_NAME=$(mac "docker ps --format '{{.Names}}' | grep '^nar-gg-' | head -1")
IMAGE=$(mac "docker inspect $CUR_NAME --format '{{.Config.Image}}'" 2>/dev/null || echo "$IMAGE_FALLBACK")
if [ "$CUR_NAME" = "nar-gg-blue" ]; then NEW=nar-gg-green; PORT=8083; else NEW=nar-gg-blue; PORT=8080; fi
echo "  $CUR_NAME → $NEW ($PORT), 이미지 $IMAGE"

# env 는 파일로 넘긴다. -e 로 인라인 전개하면 값에 공백이 있는 항목
# (JAVA_TOOL_OPTIONS="-Xmx2g --add-opens=...")에서 인자가 쪼개져 docker 가
# 엉뚱한 토큰을 이미지 이름으로 읽는다. 2026-08-17 컷오버에서 실제로 터졌다.
mac "docker inspect $CUR_NAME --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -v '^\$' > /tmp/cutover.env && chmod 600 /tmp/cutover.env"

# 시크릿 볼륨도 같이 넘겨야 한다. env 만 복사하면 GOOGLE_APPLICATION_CREDENTIALS 가
# 가리키는 파일이 없어 FirebaseApp 빈 생성에서 죽고 재시작 루프에 빠진다.
mac "docker rm -f $NEW 2>/dev/null; docker run -d --name $NEW -p 127.0.0.1:${PORT}:8080 -m 3g --restart unless-stopped \
  --env-file /tmp/cutover.env \
  -v \$HOME/nar/secrets/firebase-service-account.json:/run/secrets/firebase-service-account.json:ro \
  -v \$HOME/nar/secrets/apns-auth-key.p8:/run/secrets/apns-auth-key.p8:ro \
  -e APP_SCHEDULING_ENABLED=true -e SPRING_FLYWAY_ENABLED=true \
  -e RIOT_API_ENABLED=true -e RIOT_MONITOR_ENABLED=true -e LOL_LIVE_ENABLED=true \
  -e FIREBASE_MESSAGING_ENABLED=true -e LIVE_NOTIFICATION_FCM_ENABLED=true \
  $IMAGE" > /dev/null

for i in $(seq 1 60); do
	mac "curl -fsS -m 3 http://127.0.0.1:${PORT}/api/worlds/recent -o /dev/null" && break
	sleep 2
	[ "$i" -eq 60 ] && die "새 인스턴스가 안 뜬다 — 구 인스턴스가 계속 서비스 중이다"
done
mac "printf 'upstream nar_backend {\n    server 127.0.0.1:%s;\n}\n' $PORT > /opt/homebrew/etc/nginx/servers/nar-upstream.conf && nginx -t && nginx -s reload" > /dev/null
ok "스케줄러 ON 인스턴스로 전환 완료"

# 7. 터널에 api.nar.kr 추가
mac "python3 - <<'PY'
p='/opt/homebrew/etc/cloudflared/config.yml'
s=open(p).read()
# 주석에도 api.nar.kr 이 적혀 있어서 문자열 포함 검사로는 오판한다.
# 실제 ingress 규칙이 있는지로 본다.
if '- hostname: api.nar.kr' not in s:
    s=s.replace('  - hostname: home.nar.kr','  - hostname: api.nar.kr\n    service: http://127.0.0.1:8081\n\n  - hostname: home.nar.kr')
    open(p,'w').write(s)
PY
launchctl kickstart -k gui/\$(id -u)/com.nar.cloudflared" && ok "터널에 api.nar.kr 추가"

# 8. DNS 를 터널로. 기존 A 레코드를 덮어쓴다.
mac "cloudflared tunnel route dns --overwrite-dns nar-macmini api.nar.kr" 2>&1 | tail -1

echo
echo "=== 검증 ==="
sleep 10
for h in api.nar.kr home.nar.kr; do
	printf "  %-14s " "$h"
	curl -s -o /dev/null -w "HTTP %{http_code}  %{time_total}s\n" --max-time 15 "https://$h/api/worlds/recent"
done
mac "$MYSQL_MAC -u root -N -e 'SELECT CONCAT(\"  맥미니 read_only=\", @@read_only)'"

# 9. 이후 배포가 스케줄러를 켠 채로 뜨게 한다. 이걸 빠뜨리면 다음 배포에서
#    스케줄러가 꺼진 인스턴스로 교체돼 라이브 폴링이 조용히 멈춘다.
if command -v gh > /dev/null; then
	gh variable set APP_SCHEDULING_ENABLED --body true > /dev/null && \
	gh variable set SPRING_FLYWAY_ENABLED  --body true > /dev/null && ok "저장소 변수 true"
else
	warn "gh 없음 — 변수 두 개를 손으로 true 로 바꿔야 한다"
fi

# 10. 롤백 안전망. 춘천을 맥미니의 복제본으로 만들어 컷오버 이후 쓰기도 보존한다.
HERE="$(cd "$(dirname "$0")" && pwd)"
if [ -x "$HERE/cutover-reverse-repl.sh" ]; then
	"$HERE/cutover-reverse-repl.sh" --apply && ok "역방향 복제 설정" || warn "역방향 복제 실패 — 손으로 확인할 것"
fi

echo
echo "남은 수동 작업:"
echo "  워크플로 트리거 맞바꾸기 (deploy.yml 에서 push 제거, deploy-macmini.yml 에 추가)"
