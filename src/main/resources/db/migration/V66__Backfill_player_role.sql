-- 구독 가능 선수(2026 LCK 출전자 ∪ 솔랭 계정 보유자) 중 players.role 이 NULL 인 18명 백필.
-- role 이 NULL 이면 PlayerRoleOrder.of() 가 '기타'로 판정해 포지션 정렬에서 맨 뒤로 밀린다.
-- 근거: game_participants.position 최다 출전 포지션. players.role 표기(Top/Jungle/Mid/ADC/Support)로 매핑.
UPDATE players p
JOIN (
    SELECT player_id, position
    FROM (
        SELECT gp.player_id,
               gp.position,
               ROW_NUMBER() OVER (
                   PARTITION BY gp.player_id
                   ORDER BY COUNT(*) DESC, MAX(g.actual_game_start_time) DESC
               ) AS rn
        FROM game_participants gp
        JOIN games g ON g.game_id = gp.game_id
        GROUP BY gp.player_id, gp.position
    ) ranked
    WHERE ranked.rn = 1
) top_position ON top_position.player_id = p.player_id
SET p.role = CASE top_position.position
                 WHEN 'top' THEN 'Top'
                 WHEN 'jng' THEN 'Jungle'
                 WHEN 'mid' THEN 'Mid'
                 WHEN 'bot' THEN 'ADC'
                 WHEN 'sup' THEN 'Support'
             END
WHERE p.role IS NULL
  AND top_position.position IN ('top', 'jng', 'mid', 'bot', 'sup')
  -- 구독 가능 범위로 한정한다. 다른 리그 선수까지 건드리면 백필 범위가 수천 명으로 번진다.
  AND (
      EXISTS (
          SELECT 1
          FROM game_participants gp2
          JOIN games g2 ON g2.game_id = gp2.game_id
          JOIN leagues l2 ON l2.league_id = g2.league_id
          WHERE gp2.player_id = p.player_id
            AND l2.league_name = 'LCK'
            AND l2.season_year = 2026
      )
      OR EXISTS (
          SELECT 1
          FROM player_riot_account pra
          WHERE pra.player_id = p.player_id
            AND pra.enabled = TRUE
            AND pra.primary_account = TRUE
      )
  );

-- Deft(ADC)·Rascal(Top)은 경기 기록이 0건(백오피스 수동 추가)이라 위 백필이 닿지 않는다.
UPDATE players SET role = 'ADC' WHERE player_name = 'Deft' AND role IS NULL;
UPDATE players SET role = 'Top' WHERE player_name = 'Rascal' AND role IS NULL;
