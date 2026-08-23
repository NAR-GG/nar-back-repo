#!/usr/bin/env python3
"""match_window.py 자체 검증. 프레임워크 없이 assert 만 쓴다.

    python3 .github/scripts/test_match_window.py

이 판정이 틀리면 스케줄러가 경기 중에 재기동되거나(누락 사고) 반대로 영구히 보류된다
(옛 코드로 방치). 경계 조건이 핵심이라 픽스처로 잠근다.
"""

import json
import os
import pathlib
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).parent
SCRIPT = HERE / "match_window.py"
NOW = "2026-08-23T21:30"


def verdict(matches: list[dict]) -> str:
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        json.dump({"matches": matches}, f)
        path = f.name
    try:
        env = {**os.environ, "NOW": NOW}
        env.pop("GITHUB_OUTPUT", None)  # 테스트가 실제 출력 파일을 오염시키지 않게
        out = subprocess.run(
            [sys.executable, str(SCRIPT), path],
            capture_output=True, text=True, env=env, check=True,
        ).stdout.strip()
        return out
    finally:
        os.unlink(path)


def m(time: str, status: str) -> dict:
    return {"scheduledTime": time, "leagueInfo": "LCK", "matchTitle": "A vs B", "matchStatus": status}


def main() -> int:
    # 진행 중이면 창 안이다.
    assert verdict([m("20:00", "inProgress")]) == "clear=false"

    # 끝난 경기만 있으면 창 밖이다.
    assert verdict([m("17:00", "completed")]) == "clear=true"

    # 시작 1시간 안이면 창 안이다 — 21:30 기준 22:00 은 30분 뒤.
    assert verdict([m("22:00", "unstarted")]) == "clear=false"

    # 경계: 정확히 1시간 뒤는 창 안(<= 3600).
    assert verdict([m("22:30", "unstarted")]) == "clear=false"

    # 1시간을 넘으면 창 밖이다.
    assert verdict([m("23:30", "unstarted")]) == "clear=true"

    # 이미 지난 시각의 unstarted(자정 넘겨 시작하는 경기가 오늘 목록에 실린 경우)는
    # delta 가 음수라 "임박"으로 세지 않는다.
    assert verdict([m("00:00", "unstarted")]) == "clear=true"

    # 시간 문자열이 깨져 있으면 그 경기를 건너뛴다 — 파싱 실패로 배포가 멈추면 안 된다.
    assert verdict([m("", "unstarted"), m("TBD", "unstarted"), m("99:99", "unstarted")]) == "clear=true"

    # 경기가 없는 날.
    assert verdict([]) == "clear=true"

    # 여러 건 중 하나만 걸려도 창 안이다.
    assert verdict([m("17:00", "completed"), m("20:00", "inProgress")]) == "clear=false"

    print("모든 검증 통과")
    return 0


if __name__ == "__main__":
    sys.exit(main())
