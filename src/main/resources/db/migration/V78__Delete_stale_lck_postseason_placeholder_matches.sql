-- LCK 포스트시즌 placeholder 경기 잔재 삭제 (2026 서머)
--
-- 증상: 8/26~9/13 플레이인·플레이오프 13일 전부가 하루 2경기로 보였다.
--       실제로는 하루 1경기가 정상인 구간이고, 같은 날·같은 시각(17:00)·같은
--       Bo5 경기가 두 벌씩 들어 있었다. 한쪽은 대진이 확정된 실제 경기,
--       다른 한쪽은 'TBD vs TBD' 로 남은 옛 placeholder 다.
--
-- 원인: Riot 은 브래킷이 확정·재편될 때 경기를 **새 matchId 로 다시 만든다**.
--       옛 placeholder 는 getSchedule 응답에서 사라지는데, 우리 동기화
--       (LeagueMatchService)는 upsert 만 하고 "응답에 없어진 경기"를 지우는
--       단계가 없어 DB 에 그대로 남았다. 아래 삭제 대상에 세대가 셋
--       (1155481479007…, 1155481479008…, 1168841331898…) 섞여 있는 게
--       이 일이 한 번이 아니라 여러 번 반복됐다는 증거다.
--
-- 확인: 2026-08-25 기준 Riot getSchedule(leagueId=98767991310872058) 이
--       8/26 이후로 내려주는 경기는 정확히 13건이고 전부 1170307526448… 세대다.
--       아래 13건은 그 응답에 없다 → 다음 동기화로 되살아나지 않는다.
--
-- 범위: 아래 13개 id 만 지운다. 조건식(예: match_title LIKE '%TBD%') 을 쓰지
--       않은 이유는, 아직 대진이 안 정해진 **정상** 경기도 TBD vs TBD 이기
--       때문이다(8/28, 9/03~ 등). 그쪽은 남아야 한다.
--
-- 재발 방지는 이 마이그레이션의 범위가 아니다. 동기화에 stale 정리 단계를
-- 넣는 작업이 따로 필요하다.

SET @stale_match_ids = '115548147900750289,115548147900750295,116884133189845618,115548147900750319,115548147900750325,115548147900750331,115548147900750337,115548147900750343,115548147900750355,115548147900750349,115548147900750361,115548147900750367,115548147900815909';

-- league_match 를 참조하는 자식 행부터 정리한다.
-- 미시작 placeholder 라 보통 비어 있지만, 사용자가 알림을 걸어뒀을 수 있다.
-- 그 알림은 애초에 열리지 않을 유령 경기에 걸린 것이라 같이 지우는 게 맞다.
DELETE FROM member_match_subscription
WHERE FIND_IN_SET(match_id, @stale_match_ids) > 0;

DELETE FROM league_match_game
WHERE FIND_IN_SET(match_id, @stale_match_ids) > 0;

-- FK 가 걸려 있지 않지만 match_id 로 경기를 참조하는 테이블들.
-- 미시작 경기라 값이 없는 게 정상이고, 있다면 그것도 잔재다.
DELETE FROM live_activity_token
WHERE FIND_IN_SET(match_id, @stale_match_ids) > 0;

DELETE FROM member_team_event_push_delivery
WHERE FIND_IN_SET(match_id, @stale_match_ids) > 0;

DELETE FROM league_match
WHERE FIND_IN_SET(id, @stale_match_ids) > 0;
