# 후푸 평점 연동 기획

## 개요

중국 커뮤니티 후푸(Hupu, 虎扑)의 LoL 경기별 선수 평점과 베스트 댓글을 우리 선수 평점 화면에 함께 보여주는 기능이다. 2026-08-01~02에 API 리서치, 실측 검증, 법적 검토, UI 목업까지 끝냈고 구현만 남았다. 이 문서는 그 조사 결과의 단일 정리본이다.

조사 결론 세 줄 요약.

- 후푸 평점·핫댓글은 무인증 공개 API 3단 체인으로 전부 가져올 수 있다 (실측 검증 완료).
- 평점 숫자 표기는 법적 리스크가 낮지만, 댓글 원문 전재는 저작권법 28조 인용 요건을 못 채운다. 원문 대신 요약을 노출한다.
- 투표의 약 85%가 세트 종료 후 1~2시간 안에 몰린다. 앱 응답속도는 백엔드 폴링 + 캐시로 해결한다.

## 데이터 수집: 후푸 API 3단 체인

전부 인증·서명 없이 호출된다. 2026-08-01 GEN vs DK 경기로 실측 검증했다.

1단계, 경기 목록. 경기와 평점 노드를 매핑하는 진입점이다.

```
GET https://match-api.hupu.com/1/8.2.10/matchallapi/bff/standard/getScheduleListByTagForH5
    ?businessType=common&datasource=navigation
    &scheduleName=%E8%8B%B1%E9%9B%84%E8%81%94%E7%9B%9F%E8%B5%9B%E4%BA%8B&businessId=lol&tab=1
```

`scheduleName`(英雄联盟赛事 URL 인코딩)이 빠지면 에러 없이 빈 배열이 온다. 응답에는 matchId, 팀명, 세트 스코어, `matchStatus`(COMPLETED 등), 총 평점 인원(`scoreCountText`), 그리고 평점 노드 참조인 `scoreItemKey {outBizNo, outBizType}`가 들어 있다.

2단계, 경기 노드에서 세트(第N局) 노드. `https://m.hupu.com/score-list/{outBizType}/{outBizNo}` HTML 안의 `__NEXT_DATA__` SSR JSON을 파싱한다. `initTabNodes.nodePageResult.data[]`에 세트 노드 목록, `initSubGroup`에 groupId가 있다.

3단계, 세트에서 선수 평점과 핫댓글. 순수 JSON API다.

```
GET https://games.mobileapi.hupu.com/1/8.0.1/bplcommentapi/bpl/score_tree/groupAndSubNodes
    ?nodeId={nodeId}&groupId={groupId}
```

선수 노드마다 `scoreAvg`(10점 만점), `scorePersonCount`, `scoreDistribution`, `commentCount`, `hotCommentModels[]`(내용, 추천수 lightCount, 작성자, 아바타), `infoJson.desc`(플레이 챔피언, 중국어)가 온다. 코치 노드도 있다. 세트 2 이후에는 desc에 KDA가 붙는다.

주의할 점 두 가지.

- bizType이 대회마다 다르다. LCK/LPL 정규 경기는 `lol_match`(경기) → `lol_bo`(세트), EWC 같은 이벤트 대회는 `common_sports_first` → `common_sports_second`다. 둘 다 처리해야 한다.
- 파라미터가 틀려도 `{"code":1,"msg":"成功","data":null}`로 조용히 성공 응답이 온다. data null을 실패로 취급하는 모니터링이 필요하다.

실측 예시 (2026-08-01 LCK GEN 0-2 DK, outBizNo 3688): 1세트 ShowMaker 카시오페아 평점 9.9에 2,053명 투표, Ruler 진 2.2에 1,729명. 경기 전체 2.9만 명 참여.

## 수집 타이밍: 활성화 실측

같은 경기를 종료 직후와 11시간 뒤 두 번 조회해 비교했다.

| 시점 | 경기 전체 | ShowMaker 1세트 |
| --- | --- | --- |
| 종료 후 1~2시간 내 | 2.5만 명 | 2,053표 |
| 종료 후 11시간 | 2.9만 명 (+16%) | 2,371표 (+15%) |

투표의 약 85%가 종료 후 1~2시간에 몰린다. 후푸는 경기 전체가 아니라 세트가 끝날 때마다 평점이 열리고, 마지막 세트가 표를 가장 많이 받는다. 댓글은 평점보다 꼬리가 길어서 다음날까지 계속 늘어난다.

폴링 스케줄 설계.

1. 라이브 모니터가 세트 종료를 감지하면 10분 뒤 첫 수집을 한다. 이 시점에도 평점 순위는 사실상 확정이다.
2. 종료 후 2시간까지 10분 간격으로 갱신한다.
3. 다음날 오전에 최종 스냅샷을 한 번 뜨고, 반응 요약도 이때 확정한다. 댓글이 하루 뒤 더 풍부하기 때문이다.

앱은 후푸를 직접 호출하지 않는다. nar 백엔드가 수집해 DB에 저장하고, 앱 API는 캐시에서 즉시 응답한다.

## 법적 검토 결과

여기어때의 야놀자 크롤링 사건에서 대법원(2022-05-12)이 로그인 없이 공개된 정보의 크롤링을 정보통신망 침입, 컴퓨터장애, 저작권침해 모두 무죄로 확정했다. 후푸 API도 무인증 공개 엔드포인트라 형사 리스크는 사실상 없다.

노출 형태별 판단.

| 형태 | 리스크 | 결정 |
| --- | --- | --- |
| 평점 숫자 + 출처 표기 + 원문 링크 | 낮음 | 노출한다 |
| 여론 요약 문장 (패러프레이즈) | 낮음 | 노출한다 |
| 댓글 원문·번역 전재, 닉네임·아바타 | 높음 | 노출하지 않는다 |

댓글 저작권은 후푸가 아니라 작성자 개인 소유다. 저작권법 28조 인용 항변은 인용이 부종적일 때만 서는데, "베스트 댓글 보기" 기능은 댓글 자체가 콘텐츠의 주가 되므로 항변이 약하다. 그래서 원문 대신 사실 전달형 요약 1~2문장으로 노출한다. 삭제 요청 창구를 만들고, 기능이 자리 잡으면 후푸 제휴 문의가 근본 해결책이다. 출시 전 법률 자문 한 번 받는다. 자문 포인트는 요약과 인용의 경계 설계다.

## UI 설계

목업: https://claude.ai/code/artifact/d2fe6a2e-5900-493a-b6f8-419d932083e0 (warding-mobile-repo 디자인 토큰 그대로 제작)

화면 A, 경기 상세 선수 평점 탭.

- 전체 요약 아래 후푸 스트립 한 줄: "후푸 GEN 3.9 · DK 9.8 /10 · 2.5만명 · 원문 링크".
- 선수 행 우측에 3번째 줄 보조 지표: "후푸 9.9 · 2,053명" (11px, narText2).

화면 B, 선수 평점 상세.

- 자체 분포 섹션 아래 후푸 카드: 10점제 평균, 분포 바(후푸 레드 #FF8787), 반응 요약 1문장, 출처와 원문 링크 푸터.

설계 결정 세 가지.

- 10점제를 별점으로 환산하지 않고 /10 그대로 표기한다. 자체 5점 별점과 혼동을 막기 위해서다.
- 후푸 데이터가 없는 경기(LCK CL 등)는 스트립, 카드, 보조 줄이 아예 안 붙는다. 레이아웃 변화가 없으므로 스켈레톤도 불필요하다.
- 중국어를 UI에 그대로 노출하지 않는다. 라벨은 "후푸", 챔피언명은 한국어로 번역한다.

## 번역 방안

대상이 두 종류라 방법이 다르다.

챔피언명 같은 고정 어휘는 Riot Data Dragon 매핑으로 푼다. `champion.json`을 `zh_CN`과 `ko_KR` 로케일로 받아 챔피언 key 기준 매핑 테이블을 만든다. 무료이고 결정적이며 신챔피언도 자동으로 해결된다. 패치 버전 갱신 때 테이블만 리프레시한다.

댓글 여론은 Claude API 한 콜로 번역, 요약, 패러프레이즈를 동시에 처리한다. 커뮤니티 밈과 게임 은어 때문에 전통 번역 API는 품질이 안 나오고, 출력이 애초에 요약이라 법적 가이드(원문 전재 금지)가 파이프라인에 내장된다. 세트당 1콜(핫댓글 20~30개 입력, 선수별 요약 출력)로 하루 약 13콜이다.

| 모델 | 단가 (입력/출력, MTok) | 월 비용 추정 |
| --- | --- | --- |
| Claude Opus 5 | $5 / $25 | 약 $15 |
| Claude Haiku 4.5 | $1 / $5 | 약 $3 |

기본은 Opus 5로 시작하고 품질을 본 뒤 하향을 결정한다. 요약문은 `summary_status=PENDING_REVIEW`로 저장해 백오피스 검수 후 노출한다.

## 구현 목록

nar 백엔드.

1. 엔티티·마이그레이션: 경기/세트/선수별 후푸 평점, 핫댓글 raw, 요약문과 검수 상태.
2. `app/hupu/` 수집 클라이언트: 3단 체인 호출, bizType 두 계열 분기, data null 감지.
3. 스케줄러 잡: 세트 종료 감지 후 폴링(10분 간격 2시간 + 익일 최종), 기존 라이브 모니터에 연결. 로컬에서는 `app.scheduling.enabled` OFF 규칙을 따른다.
4. 번역 파이프라인: ddragon zh_CN 매핑 테이블, Claude 요약 콜(anthropic-java, 구조화 출력), 백오피스 검수 API.
5. 앱 조회 API: v3, 캐시 응답.

warding-mobile-repo.

1. `lib/model/hupu_rating.dart`, `lib/repository/match/hupu_rating_repository.dart`.
2. `lib/screens/match_detail/component/match_detail_hupu_strip.dart`, 기존 `match_detail_team_rating_section.dart`에 옵셔널 보조 지표.
3. `lib/screens/player_rating/component/hupu_rating_card.dart`.
4. `AppColors.hupuBrand = Color(0xFFFF8787)` 토큰 추가.

## 리스크

- 비공식 API라 언제든 서명 요구나 차단이 올 수 있다. 낮은 빈도 폴링과 서버 캐시가 전제이고, 차단 시 기능이 조용히 비노출되도록 설계한다.
- 파라미터 오류가 성공 응답으로 위장되므로 수집 결과가 비었을 때 알림이 필요하다.
- 신챔피언은 ddragon 등록 전까지 중국어명이 남을 수 있다. 매핑 실패 시 원문 대신 빈 값 처리한다.
- 요약문 자동 노출은 하지 않는다. 검수 전 노출은 오역과 법적 경계 이탈 리스크가 있다.

## 참고 자료

- 검증 스냅샷: 2026-08-01 GEN vs DK (outBizNo 3688, lol_bo 6675/6676), EWC HLE vs BRO (common_sports_first 26374)
- 대법원 여기어때 무죄 보도: https://zdnet.co.kr/view/?no=20220512180515
- 저작권법 28조: https://casenote.kr/법령/저작권법/제28조
- Claude 메모리 `hupu-rating-api`에 API 체인 요약 저장됨
