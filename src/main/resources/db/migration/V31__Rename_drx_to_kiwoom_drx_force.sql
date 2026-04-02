DELETE t
FROM teams t
LEFT JOIN game_team_stat gts ON gts.team_id = t.team_id
LEFT JOIN game_participants gp ON gp.team_id = t.team_id
LEFT JOIN league_teams lt ON lt.team_id = t.team_id
LEFT JOIN team_external_identity tei ON tei.team_id = t.team_id
WHERE t.team_name = 'Kiwoom Drx'
  AND t.team_code IS NULL
  AND gts.team_id IS NULL
  AND gp.team_id IS NULL
  AND lt.team_id IS NULL
  AND tei.team_id IS NULL;

UPDATE teams t
JOIN team_external_identity tei
  ON tei.team_id = t.team_id
 AND tei.source = 'LOLESPORTS'
 AND tei.external_team_id = '99566404585387054'
SET t.team_name = 'Kiwoom Drx',
    t.team_code = 'KRX',
    t.team_image_url = 'https://static.lolesports.com/teams/1774247803537_horizontal_EN_Wh.png'
WHERE NOT EXISTS (
    SELECT 1
    FROM teams other
    WHERE other.team_id <> t.team_id
      AND LOWER(other.team_name) = LOWER('Kiwoom Drx')
);
