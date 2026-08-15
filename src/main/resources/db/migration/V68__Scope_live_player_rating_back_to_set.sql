-- 리뷰를 매치 단위 → 세트 단위로 되돌린다 (V49 역전).
-- 한 세트에서 선수 1명당 리뷰 1개: (live_game_id, live_participant_id, member_id) 유니크.
-- 기존 행은 지우지 않는다. 리뷰는 각자 작성된 세트에 그대로 붙는다.

-- 새 유니크 키 기준 중복 정리. 매치 유니크는 player_ref 로 구분하므로,
-- 같은 세트·같은 참가자라도 esportsPlayerId 유무에 따라 player_ref 가 갈리면
-- (esportsPlayerId 는 nullable) 한 회원의 리뷰가 2행으로 남을 수 있었다.
-- 2026-08-15 프로덕션 실측으로는 0건이지만, 배포 직전 유입까지 막으려면 필요하다.
-- 남기는 쪽은 가장 최근 수정본.
DELETE stale FROM live_player_rating stale
    JOIN live_player_rating keep
        ON keep.live_game_id = stale.live_game_id
       AND keep.live_participant_id = stale.live_participant_id
       AND keep.member_id = stale.member_id
       AND (keep.updated_at > stale.updated_at
            OR (keep.updated_at = stale.updated_at AND keep.id > stale.id));

-- match_id 는 내 리뷰 목록·백오피스의 경기 정보 표시용으로만 남기고 NULL 을 허용한다.
-- 라이브 메타데이터가 매치를 못 찾는 게임에서 저장이 NOT NULL 위반으로 터지지 않게 한다.
-- idx_live_player_rating_target·idx_live_player_rating_game 은 새 유니크 키의 접두사라
-- 중복이므로 함께 정리한다(쓰기 비용만 낸다).
ALTER TABLE live_player_rating
    DROP INDEX uk_live_player_rating_match_player_member,
    DROP INDEX idx_live_player_rating_match_player,
    DROP INDEX idx_live_player_rating_target,
    DROP INDEX idx_live_player_rating_game,
    DROP COLUMN player_ref,
    MODIFY COLUMN match_id VARCHAR(64) NULL,
    ADD CONSTRAINT uk_live_player_rating_member_target
        UNIQUE (live_game_id, live_participant_id, member_id);
