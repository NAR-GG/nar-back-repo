-- 투표 옵션 3종: 복수 선택, 결과 공개 방식(기존 컬럼), 마감 시간.
-- 복수 선택은 설계(V79 주석)대로 uk 를 (poll_id, member_id, option_id) 로 넓힌다 —
-- 단일 선택 강제는 서버(allow_multiple=FALSE 검사)가 맡는다.
-- 투표 기능은 아직 앱 미출시라 두 테이블 모두 0행 — 데이터 이전 없음.
ALTER TABLE community_poll
    ADD COLUMN allow_multiple BOOLEAN NOT NULL DEFAULT FALSE AFTER hide_results_until_voted,
    ADD COLUMN closes_at DATETIME(3) NULL AFTER allow_multiple;

ALTER TABLE community_poll_vote
    DROP INDEX uk_community_poll_vote,
    ADD CONSTRAINT uk_community_poll_vote UNIQUE (poll_id, member_id, option_id);
