-- 리뷰를 매치 단위 → 세트 단위로 되돌린다 (V49 역전).
-- 한 세트에서 선수 1명당 리뷰 1개: (live_game_id, live_participant_id, member_id) 유니크.
-- 매치 유니크가 세트 유니크보다 좁은 제약이라 기존 행은 이미 새 키를 만족한다 — 데이터는 그대로 둔다.
-- match_id 는 내 리뷰 목록의 경기 정보 표시용으로만 남기고, 라이브 메타데이터가 매치를 못 찾는
-- 게임에서도 평가가 저장되도록 NULL 을 허용한다.
ALTER TABLE live_player_rating
    DROP INDEX uk_live_player_rating_match_player_member,
    DROP INDEX idx_live_player_rating_match_player,
    DROP COLUMN player_ref,
    MODIFY COLUMN match_id VARCHAR(64) NULL,
    ADD CONSTRAINT uk_live_player_rating_member_target
        UNIQUE (live_game_id, live_participant_id, member_id);
