-- 구독 가능 선수(2026 LCK 출전자 ∪ 솔랭 계정 보유자) 중 players.role 이 NULL 인 18명 백필.
-- role 이 NULL 이면 PlayerRoleOrder.of() 가 '기타'로 판정해 포지션 정렬에서 맨 뒤로 밀린다.
--
-- 값 근거는 game_participants.position 최다 출전 포지션(프로덕션 집계, PR #366 본문에 경기 수 표).
-- 파생 쿼리를 여기서 다시 돌리지 않는 이유: leagues·player_riot_account·position 이 전부
-- 베이스라인(V30) 이전 산물이라 테스트 스키마 스텁에 없고, 결과가 18행으로 확정돼 있다.
-- Deft·Rascal 은 경기 기록 0건(백오피스 수동 추가)이라 애초에 파생이 닿지 않는다.
--
-- player_name 기준으로 맞추는 이유: UNIQUE 이고, player_id 는 로컬/프로덕션이 서로 다르다.
-- ELSE role 로 두면 목록에 없는 NULL 선수는 그대로 NULL 로 남는다.
UPDATE players
SET role = CASE player_name
               WHEN 'Cloud' THEN 'Support'
               WHEN 'Cypher' THEN 'ADC'
               WHEN 'Deft' THEN 'ADC'
               WHEN 'Fenrir' THEN 'ADC'
               WHEN 'Flandre' THEN 'Top'
               WHEN 'Guardian' THEN 'Top'
               WHEN 'Guti' THEN 'Mid'
               WHEN 'Haetae' THEN 'Top'
               WHEN 'Janus' THEN 'Top'
               WHEN 'Jinbeom' THEN 'ADC'
               WHEN 'Mihawk' THEN 'Jungle'
               WHEN 'Painter' THEN 'Jungle'
               WHEN 'Quad' THEN 'Mid'
               WHEN 'Quid' THEN 'Mid'
               WHEN 'Rascal' THEN 'Top'
               WHEN 'Setab' THEN 'Mid'
               WHEN 'Sylvie' THEN 'Jungle'
               WHEN 'Zinie' THEN 'Mid'
               ELSE role
           END
WHERE role IS NULL;
