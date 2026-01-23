-- Add player profile fields for TrackingThePros data
ALTER TABLE players ADD COLUMN real_name VARCHAR(100);
ALTER TABLE players ADD COLUMN birth_date VARCHAR(10);
ALTER TABLE players ADD COLUMN age INTEGER;
ALTER TABLE players ADD COLUMN role VARCHAR(20);
ALTER TABLE players ADD COLUMN game_accounts JSON;
