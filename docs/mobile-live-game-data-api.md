# 경기 데이터 탭 API — 플러터 연동 가이드

`GET /api/mobile/live/games/{gameId}/champions` 한 응답으로 경기상세 "경기 데이터" 탭의 네 블록
(Champion Pick · Player Stats · Team Summary · Objectives)과 선수별 **빌드 시트(Player Builds)** 를 전부 그린다.
선수 전환·시트 열기에 추가 콜은 없다.


| 배포   | 내용                                        | 상태   |
| ---- | ----------------------------------------- | ---- |
| #522 | 선수 스탯·팀 합산·오브젝트·룬 아이콘 2개·`Cache-Control`  | prod |
| #524 | 아이템 섹션 분리 (코어·퀘스트·장신구·소모품)                | prod |
| #526 | 룬 전체(트리·룬 6개·설명·파편) + `frameTimestampUtc` | prod |
| #527 | 와드 설치/파괴 수 (`wardsPlaced`/`wardsDestroyed`) + DB 컬럼 V87 | PR, 머지 대기 |


응답 필드는 전부 **추가**만이라 구버전 앱은 깨지지 않는다. `gameId` 는 `/api/mobile/matches/{matchId}/games` 의 `games[].gameId`.

## 응답 예시 (실데이터, 팀당 픽 1명만 남김)

```jsonc
{
  "gameId": "115570814727903842",
  "frameTimestampUtc": "2026-07-31T12:11:23.157",
  "blueTeam": {
    "teamName": "Ctbc Flying Oyster",
    "picks": [
      "… ×5"
    ],
    "bans": [
      "… ×5 (reconcile 전엔 [])"
    ],
    "summary": {
      "kills": 17,
      "deaths": 32,
      "assists": 44,
      "creepScore": 1242,
      "totalGoldEarned": 68438
    }
  },
  "redTeam": {
    "teamName": "Gam Esports",
    "picks": [
      {
        "position": "mid",
        "championName": "Cassiopeia",
        "championImageUrl": "https://ddragon.leagueoflegends.com/cdn/img/champion/loading/Cassiopeia_0.jpg",
        "playerName": "GAM Gloryy",
        "level": 18,
        "kills": 11,
        "deaths": 3,
        "assists": 12,
        "creepScore": 309,
        "totalGoldEarned": 17633,
        "killParticipation": 0.71875,
        "championDamageShare": 0.2406591943551878,
        "itemImageUrls": [
          "…/img/item/6657.png",
          "…/img/item/3363.png",
          "…/img/item/3111.png",
          "…/img/item/6653.png",
          "…/img/item/3116.png",
          "…/img/item/3135.png"
        ],
        "coreItemImageUrls": [
          "…/img/item/6657.png",
          "…/img/item/3111.png",
          "…/img/item/6653.png",
          "…/img/item/3116.png",
          "…/img/item/3135.png"
        ],
        "questItemImageUrl": null,
        "trinketItemImageUrl": "…/img/item/3363.png",
        "consumableItemImageUrls": [],
        "keystoneIconUrl": "…/perk-images/Styles/Sorcery/DeathfireTouch/DEATHFIRE_TOUCH_KEYSTONE.png",
        "subStyleIconUrl": "…/perk-images/Styles/7201_Precision.png",
        "runes": {
          "primary": {
            "styleName": "마법",
            "styleIconUrl": "…/perk-images/Styles/7202_Sorcery.png",
            "runes": [
              {
                "name": "죽음불꽃 손길",
                "iconUrl": "…/perk-images/Styles/Sorcery/DeathfireTouch/DEATHFIRE_TOUCH_KEYSTONE.png",
                "description": "챔피언에게 스킬로 피해를 입히면 지속적으로 화상 적용"
              },
              {
                "name": "마나순환 팔찌",
                "iconUrl": "…/perk-images/Styles/Sorcery/ManaflowBand/ManaflowBand.png",
                "description": "적 챔피언에게 스킬을 적중하면 최대 마나가 영구적으로 25만큼 증가합니다. (최대 마나량: 250) 최대 마나량 250에 도달하면 5초마다 잃은 마나의 1%를 회복합니다."
              },
              {
                "name": "기민함",
                "iconUrl": "…/perk-images/Styles/Sorcery/Celerity/CelerityTemp.png",
                "description": "모든 추가 이동 속도 효과가 7% 증가하고 이동 속도 1% 증가"
              },
              {
                "name": "주문 작열",
                "iconUrl": "…/perk-images/Styles/Sorcery/Scorch/Scorch.png",
                "description": "10초마다 공격 스킬 적중 시 챔피언을 불태움"
              }
            ]
          },
          "sub": {
            "styleName": "정밀",
            "styleIconUrl": "…/perk-images/Styles/7201_Precision.png",
            "runes": [
              {
                "name": "전설: 가속",
                "iconUrl": "…/perk-images/Styles/Precision/LegendHaste/LegendHaste.png",
                "description": "적 챔피언 처치 관여 시 영구적으로 기본 스킬 가속 효과 획득"
              },
              {
                "name": "체력차 극복",
                "iconUrl": "…/perk-images/Styles/Precision/CutDown/CutDown.png",
                "description": "체력이 높은 적 챔피언에게 입히는 피해량 증가"
              }
            ]
          },
          "shards": [
            {
              "name": "적응형 능력치",
              "iconUrl": "…/statmods/statmodsadaptiveforceicon.png",
              "label": "+9"
            },
            {
              "name": "이동 속도",
              "iconUrl": "…/statmods/statmodsmovementspeedicon.png",
              "label": "+2%"
            },
            {
              "name": "체력",
              "iconUrl": "…/statmods/statmodshealthscalingicon.png",
              "label": "+10~180"
            }
          ]
        }
      }
    ],
    "bans": [],
    "summary": {
      "kills": 32,
      "deaths": 17,
      "assists": 81,
      "creepScore": 1307,
      "totalGoldEarned": 84571
    }
  },
  "objectives": {
    "blueTeam": {
      "dragons": 2,
      "dragonTypes": [
        "cloud",
        "mountain"
      ],
      "elders": 0,
      "barons": 0,
      "towers": 3,
      "inhibitors": 0
    },
    "redTeam": {
      "dragons": 3,
      "dragonTypes": [
        "mountain",
        "mountain",
        "mountain"
      ],
      "elders": 0,
      "barons": 2,
      "towers": 10,
      "inhibitors": 2
    }
  }
}
```

URL 원형: 아이템 `https://ddragon.leagueoflegends.com/cdn/{ver}/img/item/{id}.png` ·
룬 `https://ddragon.leagueoflegends.com/cdn/img/perk-images/…` ·
파편 `https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/perk-images/statmods/…` ·
챔피언은 Cloudinary 경유 CommunityDragon 스플래시(400×600, 세로 카드용).

## 블록별 매핑

### Champion Pick

- `picks[]` 는 항상 **top → jungle → mid → bottom → support** 순. `position` 값도 그 5개.
- `bans[]` 는 **reconcile 전엔 빈 배열**. 배치 적재가 6시간 주기라 갓 끝난 경기는 다음 적재 후 채워진다. 빈 칸 5개로 그려라.

### Player Stats (스코어보드 행)


| 시안       | 필드                                   | 비고                                  |
| -------- | ------------------------------------ | ----------------------------------- |
| 레벨 배지    | `level`                              |                                     |
| 룬 아이콘 2개 | `keystoneIconUrl`, `subStyleIconUrl` | 핵심룬 + 부트리                           |
| KDA      | `kills / deaths / assists`           |                                     |
| CS · 골드  | `creepScore`, `totalGoldEarned`      | 골드는 `14.3k` 식 축약은 앱에서               |
| 킬관여      | `killParticipation`                  | 0~1 실수. `85%` 로                     |
| 와드 | `wardsPlaced`, `wardsDestroyed` | 설치 / 파괴 개수(누적). **시야점수가 아니다.** 라이브 수집분(2026-09-03~)은 즉시, 그 전 경기는 종료 후 배치 CSV 로 채움(reconcile 전엔 null → 칸 숨김) |
| 딜분배      | `championDamageShare`                | 0~1 실수. **분모가 자기 팀 5명** — 팀 간 비교 불가 |
| 아이템 8칸   | 아래 아이템 절                             |                                     |


팀 헤더 `16 / 3 / 55 · 64.6k` = `summary.kills/deaths/assists`, `summary.totalGoldEarned`. 승/패 배지는 앱이 이미 세는 세트 승자.

### 아이템 — 칸은 고정으로 그린다

```
[core 1][core 2][core 3][core 4][core 5][core 6]   [quest][trinket]
```

- `coreItemImageUrls` 는 **길이 0~6**. 6칸을 항상 그리고 빈 자리는 빈 네모(op.gg 방식). 길이만큼 그리면 행 폭이 들쑥날쑥해진다.
- `questItemImageUrl` — 2026 바텀 퀘스트 완료 시 **신발이 7번째 칸으로 이동**한다. 그 신발. 아니면 `null` → 빈 네모. 원딜 외엔 거의 항상 null(실측 코어 7인 선수 전부 bottom).
- `trinketItemImageUrl` — 장신구. 안 샀으면 `null`.
- `consumableItemImageUrls` — 제어와드·물약·영약. 스코어보드 행엔 자리가 없으니 안 그려도 된다. 서포터 퀘스트 칸(제어와드 전용)은 여기로 들어온다.
- `itemImageUrls` — 원본 평면 배열(구매 순서, 장신구 섞임). 위 4개가 있으니 쓰지 말 것. 호환용으로만 남긴 필드.
- 아이콘 URL 이 없는 폐기 아이템(3097 등)은 서버가 칸을 만들지 않는다. 깨진 이미지 대비 불필요.

### Team Summary

- 총킬 `summary.kills`, CS `summary.creepScore`, 골드 `summary.totalGoldEarned` — 양 팀 값을 좌우로.
- **골드 바는 비율이 아니라 차이 바**로: 롤 골드 점유율은 45~55%에 뭉쳐 비율로 그리면 항상 반반이다. 중앙 0 기준 ±10k 클램프 권장.
- **Total Damage 절대값은 없다.** 피드가 `championDamageShare`(팀 내부 비중)만 준다. 시안의 딜량 바 자리는 골드로. 절대 딜량은 종료 후 CSV 에만 있어 나중에 승격 가능.

### Objectives


| 행             | 필드                                                                                                    |
| ------------- | ----------------------------------------------------------------------------------------------------- |
| 드래곤 수 + 속성 칩  | `objectives.{side}.dragons`, `dragonTypes[]` (획득 순, `infernal/ocean/cloud/mountain/hextech/chemtech`) |
| 장로            | `elders` — 드래곤 수에 **포함되지 않음**, 별도                                                                     |
| 바론 / 타워 / 억제기 | `barons` / `towers` / `inhibitors`                                                                    |


전령·유충·아타칸은 피드에 이벤트 자체가 없다. 행 추가 여지 없음.

### Player Builds (빌드 시트) — #526 머지 후

- 헤더 Lv·이름·챔피언·포지션·진영은 스코어보드와 같은 `Pick`. 세트 번호는 앱이 안다.
- 스탯 5칸 = 위 Player Stats 와 동일 필드.
- 아이템 = 위 아이템 절과 동일(코어 6 + 퀘스트 + 장신구).
- 룬:
  - `runes.primary` / `runes.sub` — `styleName`("결의"), `styleIconUrl`, `runes[]`. **주 4개(첫 원소가 핵심룬), 부 2개**, 슬롯 순.
  - 각 룬 `name`, `iconUrl`, `description`. `description` 은 툴팁(가이드) 문구. 평문, 마크업 없음. 게임 클라이언트 룬 선택창 짧은 설명과 같은 문장.
  - `runes.shards[]` — `name`, `iconUrl`, `label`. 칩 텍스트 = `name + " " + label` (예: `적응형 능력치 +9`). `label` 이 null 인 옛 파편은 name 만.
- **파편 칩은 2~3개 가변이다.** 같은 파편을 두 칸에 찍으면 피드가 하나로 합쳐 보낸다(전체의 25%). 어느 쪽이 중복이었는지 복원 불가라 그대로 내린다. 고정 3칸 그리드 금지 — 넘치면 아래로 쌓이는 wrap 으로.
- `runes` 가 `null` 이면(라이브 첫 프레임 전) 룬 섹션 숨김.
- 10명 스와이프는 응답 안에서 로컬 전환. 콜 없음.

파편 7종(실제 등장) — 아이콘·라벨은 서버가 준다. 참고용:


| id   | name        | label                  |
| ---- | ----------- | ---------------------- |
| 5008 | 적응형 능력치     | +9                     |
| 5005 | 공격 속도       | +10%                   |
| 5007 | 스킬 가속       | +8                     |
| 5010 | 이동 속도       | +2%                    |
| 5001 | 체력 증가       | +65                    |
| 5011 | 체력          | +10~180                |
| 5013 | 강인함 및 둔화 저항 | +10% (시안 +15% — 확인 필요) |


시안 "능력치 칩 모음" 2행 2번째 **"스킬가속"은 실제 2행에 없다**. 그 자리는 이동 속도 +2%.

## 시점 · 갱신

- `frameTimestampUtc` — 응답이 기준한 프레임 시각(UTC, 타임존 접미 없음). 라이브면 최신 폴링(5초 해상도), 종료 경기면 마지막 분 스냅샷. "21분 기준" 같은 라벨은 이걸로: 게임 시작 시각은 아직 응답에 없으니 일단 **"HH:mm 기준(KST)"** 또는 "경기 종료 시점"으로.
- 서버 `Cache-Control: max-age=10`. 앱 폴링 타이머는 **지금 안 넣는다** — 스코어보드 값은 DB 해상도 1분이라 5초 폴링이 무의미하고, 킬 알림은 라이브 이벤트 탭·푸시가 담당. pull-to-refresh 로 충분. 나중에 필요해지면 `Timer.periodic(30s)` 한 줄, 서버는 준비돼 있음.
- 라이브 중엔 `killParticipation`·`championDamageShare` 가 초반 5분 튄다(표본 작음). 값 그대로 보여도 되지만 그래프로는 쓰지 말 것.

## 없는 것 (요청 와도 못 준다)


| 항목             | 이유                                          |
| -------------- | ------------------------------------------- |
| 소환사 스펠         | 피드 `window`·`details` 어디에도 없음. CSV 도 없음     |
| 팀 간 절대 딜량      | 피드는 팀 내부 비중만. 종료 후 CSV 에만                   |
| 시야점수 | 피드는 와드 설치/파괴 **개수**만 — `wardsPlaced`/`wardsDestroyed` 로 내려간다. 시야점수는 시간 기반이라 개수로 못 만든다 |
| 전령·유충·아타칸      | 이벤트 타입 자체가 없음                               |
| 게임 시작 시각 · 경과분 | 응답에 아직 없음. 필요하면 서버 한 줄                      |


## 결측 대비

- **한 세트가 통째로 비어 있을 수 있다**(라이브 수집 0행). LCK·LPL 은 0건이지만 LEC·LCS·CBLOL 은 있다. `picks` 가 비면 Player Stats·Team Summary·Objectives 섹션 숨기고 Champion Pick 만(밴은 다른 경로). 서버는 이때 404 가 아니라 빈 `picks` 를 줄 수도, 상태가 없으면 404 를 줄 수도 있다 — 둘 다 처리.
- 분 버킷 구멍(LCK 1.7%)은 마지막 스냅샷이 실제 종료보다 몇 분 앞설 수 있다는 뜻. "경기 종료 시점" 대신 시각 라벨을 쓰는 이유.
- 모든 숫자 필드는 첫 프레임에서 `0`, 드물게 `null`. null-safe 로.

## 관련

- 백엔드 PR: #522 (스코어보드·집계), #524 (아이템 섹션), #526 (룬 전체)
- 목업 원본: `docs/mockup-match-detail-build.html`

