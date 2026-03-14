ALTER TABLE player_riot_account
    MODIFY summoner_id VARCHAR(128) NULL;

UPDATE player_riot_account
SET summoner_id = NULL
WHERE summoner_id = '';
