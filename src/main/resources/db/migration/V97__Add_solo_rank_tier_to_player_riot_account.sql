-- 계정별 솔랭 티어(Riot league-v4). 크롤링 game_accounts 는 LCK 선수만 채워져
-- 해외 선수(월즈 부트캠프 NA1 계정 등) 티어가 비었다. 컬럼 추가만이라 후방호환.
ALTER TABLE player_riot_account
    ADD COLUMN solo_tier VARCHAR(20) NULL,
    ADD COLUMN solo_division VARCHAR(4) NULL,
    ADD COLUMN solo_lp INT NULL,
    ADD COLUMN solo_tier_checked_at DATETIME(6) NULL;
