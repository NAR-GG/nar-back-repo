-- 선수당 1계정 제약을 푼다. 월즈 부트캠프 현지(NA1) 계정처럼 기간 한정 보조 계정을 주계정과 함께 폴링하기 위해서다.
-- 보조 계정은 primary_account = false 로 넣는다 — 구독·sync 쿼리는 전부 primary_account = true 조건이라 영향이 없다.
-- FK(player_id)가 기대던 유니크 인덱스를 지우기 전에 일반 인덱스를 먼저 만든다(없으면 DROP 이 거절된다).
CREATE INDEX idx_player_riot_account_player ON player_riot_account (player_id);

ALTER TABLE player_riot_account DROP INDEX uk_player_riot_account_player;
