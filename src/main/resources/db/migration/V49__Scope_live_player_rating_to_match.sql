-- 리뷰를 세트 단위에서 매치 단위로 전환.
-- 한 매치에서 선수 1명당 리뷰 1개: (match_id, player_ref, member_id) 유니크.
-- player_ref = esportsPlayerId 우선, 없으면 "name:{playerName}" (세트 무관 선수 식별 키).
-- 기존 row는 세트별 테스트 데이터뿐이라 정리 후 새 키로 시작한다.
DELETE FROM live_player_rating;

ALTER TABLE live_player_rating
    ADD COLUMN match_id VARCHAR(64) NOT NULL AFTER id,
    ADD COLUMN player_ref VARCHAR(128) NOT NULL AFTER live_participant_id;

ALTER TABLE live_player_rating
    DROP INDEX uk_live_player_rating_member_target;

ALTER TABLE live_player_rating
    ADD CONSTRAINT uk_live_player_rating_match_player_member
        UNIQUE (match_id, player_ref, member_id);

ALTER TABLE live_player_rating
    ADD INDEX idx_live_player_rating_match_player (match_id, player_ref);
