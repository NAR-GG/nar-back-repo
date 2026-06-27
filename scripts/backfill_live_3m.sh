#!/usr/bin/env bash
# 최근 3개월 LCK 라이브(분단위) 데이터 백필. EC2 호스트에서 실행.
# (MSI는 내일 시작이라 과거 백필 대상 없음 — 폴링으로 실시간 수집됨)
# 외부 livestats 피드는 ~3개월만 보존하므로 그 이상은 받아올 수 없다.
#
# 사전:
#   - 앱이 localhost:8080 에서 떠 있고 LOL_LIVE_ENABLED=true 로 배포됨
#   - mysql 클라이언트로 Oracle MySQL 접근 가능 (DB_* 환경변수)
#   - 먼저 MSI 매치 메타/gameId 동기화: 아래 0단계
#
# ponytail: 배치 백필 엔드포인트 신설 대신 기존 per-game 엔드포인트 + SQL 루프.
#           게임 수가 수천으로 늘면 그때 배치 API 추가.
set -euo pipefail

APP="${APP_URL:-http://localhost:8080}"
: "${DB_HOST:?}" "${DB_PORT:=3306}" "${DB_USER:?}" "${DB_PASSWORD:?}" "${DB_NAME:?}"
SLEEP="${SLEEP_BETWEEN:-3}"   # 게임 간 대기(초). 외부 API 부하 방지.

mysql_q() { mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -N -B -e "$1"; }

# 0단계(수동, 1회): LCK 매치/gameId 동기화 — 안 돼 있으면 백필할 gameId가 없다.
#   curl -fsS -X POST "$APP/api/admin/data/sync/matches/recent-backfill?league=LCK&includeTeamMetadata=true"
# 3개월이 1페이지에 안 들어오면(과거 더 필요) 전체 히스토리:
#   curl -fsS -X POST "$APP/api/admin/data/sync/matches/history"

# 최신순, 최근 3개월, 아직 스냅샷 없는 gameId만.
SQL="SELECT g.game_id
     FROM league_match_game g JOIN league_match m ON m.id = g.match_id
     WHERE m.league_name = 'LCK'
       AND m.match_date >= DATE_SUB(NOW(), INTERVAL 3 MONTH)
       AND g.game_id NOT IN (SELECT game_id FROM live_game_minute_snapshot)
     ORDER BY m.match_date DESC"

mapfile -t GAME_IDS < <(mysql_q "$SQL")
echo "백필 대상 게임: ${#GAME_IDS[@]}개"

ok=0; empty=0; fail=0
for gid in "${GAME_IDS[@]}"; do
  [ -z "$gid" ] && continue
  resp=$(curl -fsS -X POST "$APP/api/live/games/$gid/backfill" || echo '{"snapshotsWritten":-1}')
  written=$(echo "$resp" | grep -o '"snapshotsWritten":[0-9-]*' | cut -d: -f2)
  case "${written:-}" in
    -1|"") fail=$((fail+1)); echo "FAIL  $gid";;
    0)     empty=$((empty+1)); echo "EMPTY $gid (피드 보존기간 초과 가능)";;
    *)     ok=$((ok+1)); echo "OK    $gid  +${written} snapshots";;
  esac
  sleep "$SLEEP"
done

echo "완료: ok=$ok empty=$empty fail=$fail"
