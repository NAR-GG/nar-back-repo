#!/usr/bin/env bash
# 역방향 복제: 춘천(원본이었던 곳)을 맥미니의 복제본으로 만든다.
#
# 컷오버 직후에 실행한다. 목적은 롤백 안전망이다 — 컷오버 후 문제가 생겨 춘천으로
# 되돌릴 때, 그동안 맥미니에 들어온 쓰기를 잃지 않게 한다. 이게 없으면 롤백이
# "컷오버 이후 데이터 버리기"가 된다.
#
# 지금 미리 걸 수는 없다. 춘천이 아직 쓰기를 받는 원본이라, 양방향이 되면
# 순환 복제로 같은 행이 서로를 덮어쓴다.
#
# 실행 위치: 노트북(양쪽에 SSH 가 되는 곳). Tailscale 안이면 어디서든 된다.
#
# 사용법:
#   ./cutover-reverse-repl.sh --check    전제 조건만 확인하고 아무것도 안 바꾼다
#   ./cutover-reverse-repl.sh --apply    실제로 역방향 복제를 건다
set -euo pipefail

MACMINI_SSH="changha@macmini"
MACMINI_TS="100.111.167.92"
CHUNCHEON_SSH="ubuntu@168.107.37.215"
CHUNCHEON_KEY="$HOME/.ssh/id_ed25519"
MYSQL_MAC="/opt/homebrew/opt/mysql@8.4/bin/mysql"

MODE="${1:---check}"

mac()  { ssh -o ConnectTimeout=10 "$MACMINI_SSH" "$@"; }
chun() { ssh -o ConnectTimeout=10 -i "$CHUNCHEON_KEY" "$CHUNCHEON_SSH" "$@" 2>/dev/null | grep -v "post-quantum\|store now\|may need to be upgraded\|^\*\*"; }

fail() { echo "❌ $1"; exit 1; }
ok()   { echo "✅ $1"; }

echo "=== 전제 조건 확인 ==="

# 1. 맥미니가 쓰기 가능해야 한다. 아직 read_only 면 컷오버 앞 단계가 안 끝난 것이다.
RO=$(mac "$MYSQL_MAC -u root -N -e 'SELECT CONCAT(@@read_only,@@super_read_only)'")
if [ "$RO" = "00" ]; then
	ok "맥미니 쓰기 가능 (read_only=0)"
else
	echo "⚠️  맥미니가 아직 read_only=$RO — 컷오버 전이라면 정상이다"
	[ "$MODE" = "--apply" ] && fail "맥미니를 먼저 쓰기 가능으로 바꿔야 한다"
fi

# 2. 맥미니가 아직 춘천을 복제 중이면 안 된다. 그대로 두면 양방향이 된다.
if mac "$MYSQL_MAC -u root -e 'SHOW REPLICA STATUS\G'" | grep -q "Replica_IO_Running: Yes"; then
	echo "⚠️  맥미니가 아직 춘천을 복제 중이다 (컷오버 전이라면 정상)"
	[ "$MODE" = "--apply" ] && fail "먼저 맥미니에서 STOP REPLICA 를 해야 한다 — 안 그러면 순환 복제가 된다"
else
	ok "맥미니 복제 중지됨"
fi

# 3. 춘천이 read_only 여야 한다. 쓰기가 열린 채로 복제본이 되면 충돌한다.
CRO=$(chun "sudo mysql -N -e 'SELECT @@read_only'" | tr -d '[:space:]')
if [ "$CRO" = "1" ]; then
	ok "춘천 read_only=1"
else
	echo "⚠️  춘천이 아직 쓰기 가능하다 (read_only=$CRO)"
	[ "$MODE" = "--apply" ] && fail "춘천을 먼저 read_only 로 잠가야 한다"
fi

# 4. 춘천에서 맥미니 MySQL 로 접속이 되는지. 여기서 막히면 복제가 시작조차 안 된다.
PWLEN=$(chun "sudo sh -c 'wc -c < /root/.nar-reverse-repl-pw'" | tr -d '[:space:]')
[ "${PWLEN:-0}" -gt 10 ] || fail "춘천에 /root/.nar-reverse-repl-pw 가 없다"
ok "역방향 복제 비밀번호 배치됨 (${PWLEN}바이트)"

if chun "mysql -h $MACMINI_TS -u repl_back -p\$(sudo cat /root/.nar-reverse-repl-pw) -N -e 'SELECT 1'" | grep -q 1; then
	ok "춘천 → 맥미니 MySQL 접속 가능"
else
	fail "춘천에서 맥미니 MySQL 에 못 붙는다 (bind_address·계정·Tailscale 확인)"
fi

if [ "$MODE" != "--apply" ]; then
	echo
	echo "확인만 했다. 실제 적용은 --apply 로."
	exit 0
fi

echo
echo "=== 역방향 복제 적용 ==="

# 맥미니의 현재 binlog 좌표. 8.4 는 SHOW MASTER STATUS 가 SHOW BINARY LOG STATUS 로 바뀌었다.
COORD=$(mac "$MYSQL_MAC -u root -N -e 'SHOW BINARY LOG STATUS'" | awk '{print $1" "$2}')
LOG_FILE=$(echo "$COORD" | awk '{print $1}')
LOG_POS=$(echo "$COORD" | awk '{print $2}')
[ -n "$LOG_FILE" ] || fail "맥미니 binlog 좌표를 못 읽었다"
echo "맥미니 좌표: $LOG_FILE / $LOG_POS"

chun "sudo mysql -e \"
STOP REPLICA;
RESET REPLICA ALL;
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='$MACMINI_TS',
  SOURCE_PORT=3306,
  SOURCE_USER='repl_back',
  SOURCE_PASSWORD='\$(sudo cat /root/.nar-reverse-repl-pw)',
  SOURCE_LOG_FILE='$LOG_FILE',
  SOURCE_LOG_POS=$LOG_POS;
START REPLICA;\""

sleep 8
echo "=== 결과 ==="
chun "sudo mysql -e 'SHOW REPLICA STATUS\G'" | grep -E "Replica_IO_Running|Replica_SQL_Running:|Seconds_Behind_Source|Last_IO_Error:|Last_SQL_Error:"
