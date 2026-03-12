#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${1:-https://api.nar.kr}"
UTTERANCE="${2:-오늘 LCK 일정 알려줘}"

curl -sS \
  -X POST "${BASE_URL}/api/kakao/skills/schedule" \
  -H "Content-Type: application/json" \
  -d "{
    \"userRequest\": {
      \"utterance\": \"${UTTERANCE}\"
    }
  }"
