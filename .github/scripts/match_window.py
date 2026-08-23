#!/usr/bin/env python3
"""경기 창(시작 1시간 전 ~ 마지막 세트 종료)에 들어와 있는지 판정한다.

스케줄러 파드 재기동은 replicas 1 + Recreate 라 원리적으로 무중단이 불가능하다(실측 공백
38~48초, 부팅만 17초). 그 공백에 세트 첫 프레임이 떨어지면 그 세트의 시작 알림·라이브위젯이
통째로 누락되고 복구 방법이 없다. 그래서 배포 파이프라인이 이 판정을 보고 스케줄러 태그를
보류한다(웹은 RollingUpdate 라 공백이 0이므로 항상 배포한다).

출력은 GITHUB_OUTPUT 형식 한 줄(`clear=true|false`)과 사람이 읽을 근거를 stderr 로 낸다.
종료코드는 항상 0 이다 — 판정 실패도 "창 안"으로 취급해 호출부가 분기할 수 있게 한다.

일정을 못 읽으면 clear=false 다. 모르는 채로 스케줄러를 건드리는 것보다 배포를 미루는 쪽이
싸다(보류분은 scheduler-catchup.yml 이 가져간다).

사용:
    python3 match_window.py                 # API 를 직접 조회
    python3 match_window.py fixture.json    # 파일에서 읽는다(테스트용)
    NOW=2026-08-23T21:30 python3 ...        # 기준 시각 고정(테스트용, KST)
"""

import datetime
import json
import os
import sys
import urllib.request
import zoneinfo

KST = zoneinfo.ZoneInfo("Asia/Seoul")

# 경기 창의 앞쪽 여유. CLAUDE.md 의 "시작 1시간 전" 과 같은 값이다.
LEAD_SECONDS = 3600


def now_kst() -> datetime.datetime:
    override = os.environ.get("NOW")
    if override:
        return datetime.datetime.fromisoformat(override).replace(tzinfo=KST)
    return datetime.datetime.now(KST)


def load(argv: list[str], today: str) -> dict | None:
    if len(argv) > 1:
        with open(argv[1], encoding="utf-8") as f:
            return json.load(f)
    url = f"https://api.nar.kr/api/schedule?date={today}"
    # User-Agent 를 반드시 넣는다. urllib 기본값("Python-urllib/3.x")은 Cloudflare 가 403 으로
    # 막는다 — 실측으로 밟았고, 안 넣으면 게이트가 항상 "읽기 실패 → 창 안"이 되어 스케줄러
    # 배포가 영구히 보류된다. curl 로는 되니 로컬 확인만으로는 안 드러난다.
    req = urllib.request.Request(url, headers={"User-Agent": "nar-deploy-gate/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=15) as res:
            return json.loads(res.read().decode("utf-8"))
    except Exception as e:  # noqa: BLE001 — 원인 종류와 무관하게 "못 읽었다" 하나로 다룬다
        print(f"일정 API 실패: {e}", file=sys.stderr)
        return None


def main() -> int:
    now = now_kst()
    data = load(sys.argv, now.strftime("%Y-%m-%d"))
    if data is None:
        emit(False, "일정을 읽지 못했다 — 보수적으로 창 안으로 본다")
        return 0

    matches = data.get("matches") or []
    live = [m for m in matches if m.get("matchStatus") == "inProgress"]
    soon = []
    for m in matches:
        if m.get("matchStatus") != "unstarted":
            continue
        start = parse_start(m.get("scheduledTime"), now)
        if start is None:
            continue
        delta = (start - now).total_seconds()
        if 0 <= delta <= LEAD_SECONDS:
            soon.append(m)

    for m in live:
        print(f"  진행 중: {label(m)}", file=sys.stderr)
    for m in soon:
        print(f"  임박:    {label(m)}", file=sys.stderr)

    clear = not live and not soon
    emit(clear, f"진행 중 {len(live)}건, 1시간 내 시작 {len(soon)}건")
    return 0


def parse_start(text: str | None, now: datetime.datetime) -> datetime.datetime | None:
    """`"20:00"` → 오늘 그 시각(KST).

    자정을 넘겨 시작하는 경기(`"00:00"`)는 오늘 목록에 오늘 날짜로 실린다. 지금이 23시일 때
    그걸 "1시간 뒤"로 보면 안 되므로, 이미 지난 시각은 음수 delta 가 되어 자연히 걸러진다.
    """
    if not text:
        return None
    try:
        hh, mm = (int(x) for x in text.split(":"))
    except (ValueError, AttributeError):
        return None
    if not (0 <= hh <= 23 and 0 <= mm <= 59):
        return None
    return now.replace(hour=hh, minute=mm, second=0, microsecond=0)


def label(match: dict) -> str:
    return f"{match.get('scheduledTime')} {match.get('leagueInfo')} {match.get('matchTitle')}"


def emit(clear: bool, reason: str) -> None:
    print(reason, file=sys.stderr)
    line = f"clear={'true' if clear else 'false'}"
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    print(line)


if __name__ == "__main__":
    sys.exit(main())
