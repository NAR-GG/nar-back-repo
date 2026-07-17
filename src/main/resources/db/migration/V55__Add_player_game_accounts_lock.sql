-- 솔랭 계정(game_accounts) 수동 잠금. true면 프로필 크롤러(매일 05:30)가 game_accounts를 덮어쓰지 못한다.
ALTER TABLE players
    ADD COLUMN game_accounts_locked BOOLEAN NOT NULL DEFAULT FALSE;
