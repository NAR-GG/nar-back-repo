package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.Player;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PlayerRepository extends JpaRepository<Player, Long> {
	Optional<Player> findByName(String name);

	// 백오피스 수정 응답이 트랜잭션 밖(OSIV off)에서 currentTeam을 직렬화하므로 함께 로딩한다.
	@EntityGraph(attributePaths = {"currentTeam"})
	Optional<Player> findWithCurrentTeamById(Long id);

	// 백오피스 검색(리그 필터 없음): 선수명·실명 부분일치. q 가 null 이면 전체.
	// currentTeam은 목록 응답에 팀명을 실어야 해서 EntityGraph로 함께 로딩(N+1/LAZY 예외 방지).
	@EntityGraph(attributePaths = {"currentTeam"})
	@Query("""
			SELECT p FROM Player p
			WHERE (:q IS NULL
			       OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
			       OR LOWER(p.realName) LIKE LOWER(CONCAT('%', :q, '%')))
			""")
	Page<Player> searchForBackoffice(@Param("q") String q, Pageable pageable);

	// 백오피스 검색(리그 필터): 출전 기록(GameParticipant→Game→League) 기준 EXISTS(전 시즌 통합).
	// ⚠️ 리그 없는 검색과 합쳐서 "(:league IS NULL OR EXISTS …)" 한 방 쿼리로 만들지 말 것:
	//    EXISTS가 OR 안에 들어가면 MySQL이 semijoin 변환을 못 해 선수마다 상관 서브쿼리가 돌아
	//    쿼리당 1.7초(참가기록 14.7만행 순회)가 걸렸다. 순수 AND 조건이면 93ms.
	// ⚠️ league_teams 기준으로 바꾸지 말 것: 오염돼 있음(LCK에 462팀 등록 → 필터 전체 통과).
	@EntityGraph(attributePaths = {"currentTeam"})
	@Query("""
			SELECT p FROM Player p
			WHERE (:q IS NULL
			       OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
			       OR LOWER(p.realName) LIKE LOWER(CONCAT('%', :q, '%')))
			  AND EXISTS (SELECT 1 FROM GameParticipant gp
			              WHERE gp.player = p AND gp.game.league.leagueName = :league)
			""")
	Page<Player> searchForBackofficeInLeague(@Param("q") String q, @Param("league") String league, Pageable pageable);

	// 해당 리그 출전 이력 여부. 백오피스 선수 수정의 "LCK만 허용" 서버 검증에 사용.
	@Query("""
			SELECT COUNT(gp) > 0 FROM GameParticipant gp
			WHERE gp.player.id = :playerId AND gp.game.league.leagueName = :leagueName
			""")
	boolean hasLeagueParticipation(@Param("playerId") Long playerId, @Param("leagueName") String leagueName);

	Optional<Player> findByPlayerOriginId(String playerOriginId);

	List<Player> findAllByNameInIgnoreCase(Set<String> names);

	@Query("SELECT DISTINCT p FROM Player p " +
		"JOIN GameParticipant gp ON gp.player = p " +
		"JOIN gp.game g " +
		"JOIN g.league l " +
		"WHERE l.leagueName = :leagueName")
	List<Player> findPlayersByLeagueName(@Param("leagueName") String leagueName);

	// 솔랭 계정 sync 대상: 해당 리그 출전자 ∪ 유효 계정(enabled·primaryAccount·KR) 보유자(비출전 은퇴 선수 포함).
	// OR-EXISTS라 semijoin이 안 되지만, 하루 1회 배치이고 대상 수가 적어 성능 영향 없다.
	// ⚠️ 계정 서브쿼리에 자격 조건(enabled=true, primaryAccount=true, platform='KR') 필수.
	//    조건 없으면 비활성(enabled=false) 계정 보유자도 포함되어
	//    sync가 extractPrimaryKrAccount로 재해석 후 enabled=true 로 되돌리는 버그 발생.
	@Query("""
			SELECT DISTINCT p FROM Player p
			WHERE EXISTS (SELECT 1 FROM GameParticipant gp
			              WHERE gp.player = p AND gp.game.league.leagueName = :leagueName)
			   OR EXISTS (SELECT 1 FROM PlayerRiotAccount pra
			              WHERE pra.player = p
			                AND pra.enabled = true
			                AND pra.primaryAccount = true
			                AND pra.platform = 'KR')
			""")
	List<Player> findSoloRankSyncTargets(@Param("leagueName") String leagueName);

	@Query("""
			SELECT DISTINCT p
			FROM GameParticipant gp
			JOIN gp.player p
			JOIN gp.team t
			JOIN gp.game g
			JOIN g.league l
			WHERE l.leagueName = :leagueName
			  AND l.seasonYear = :year
			  AND (:teamId IS NULL OR t.id = :teamId)
			ORDER BY p.name
			""")
	List<Player> findOnboardingPlayers(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("teamId") Long teamId);

	/**
	 * 해당 리그·시즌에서 선수별로 "가장 최근 경기"의 팀 하나로 중복 제거해 페이지로 반환한다.
	 *
	 * <p>예전에는 각 행마다 상관 서브쿼리({@code = MAX(...)})로 최신 경기 시각을 구해
	 * 선수 수의 수십 배에 달하는 중첩 반복이 일어나 페이지당 8~9초가 걸렸다. 이를 윈도우 함수
	 * {@code ROW_NUMBER()}로 바꿔 참가 기록을 한 번만 스캔하도록 했다(동일 데이터 기준 ~55배 단축).
	 * 또한 동일 시각 경기가 둘이어도 {@code game_id} 타이브레이크로 한 행만 남아 중복이 사라진다.
	 *
	 * <p>윈도우 함수와 파생 테이블을 쓰기 위해 네이티브 쿼리로 작성했다. 컬럼 별칭은
	 * {@link LckPlayerOption} 프로젝션 게터명과 일치시킨다.
	 */
	@Query(
			value = """
					SELECT playerId, playerName, playerImageUrl, role,
					       teamId, teamCode, teamName, teamImageUrl
					FROM (
						SELECT
							p.player_id AS playerId,
							p.player_name AS playerName,
							p.image_url AS playerImageUrl,
							p.role AS role,
							t.team_id AS teamId,
							t.team_code AS teamCode,
							t.team_name AS teamName,
							t.team_image_url AS teamImageUrl,
							ROW_NUMBER() OVER (
								PARTITION BY p.player_id
								ORDER BY g.actual_game_start_time DESC, g.game_id DESC
							) AS rn
						FROM game_participants gp
						JOIN games g ON gp.game_id = g.game_id
						JOIN leagues l ON g.league_id = l.league_id
						JOIN players p ON gp.player_id = p.player_id
						JOIN teams t ON gp.team_id = t.team_id
						WHERE l.league_name = :leagueName
						  AND l.season_year = :year
						UNION ALL
						SELECT
							p.player_id AS playerId,
							p.player_name AS playerName,
							p.image_url AS playerImageUrl,
							p.role AS role,
							NULL AS teamId,
							NULL AS teamCode,
							NULL AS teamName,
							NULL AS teamImageUrl,
							1 AS rn
						FROM players p
						JOIN player_riot_account pra ON pra.player_id = p.player_id
						WHERE pra.enabled = true
						  AND pra.primary_account = true
						  AND pra.platform = 'KR'
						  AND NOT EXISTS (
							  SELECT 1 FROM game_participants gp2
							  JOIN games g2 ON gp2.game_id = g2.game_id
							  JOIN leagues l2 ON g2.league_id = l2.league_id
							  WHERE gp2.player_id = p.player_id
							    AND l2.league_name = :leagueName
							    AND l2.season_year = :year
						  )
					) ranked
					WHERE ranked.rn = 1
					  AND (:teamId IS NULL OR ranked.teamId = :teamId)
					  AND (:query IS NULL OR LOWER(ranked.playerName) LIKE LOWER(CONCAT('%', :query, '%')))
					ORDER BY CASE UPPER(ranked.role)
					             WHEN 'TOP' THEN 1
					             WHEN 'JUNGLE' THEN 2
					             WHEN 'MID' THEN 3
					             WHEN 'ADC' THEN 4
					             WHEN 'SUPPORT' THEN 5
					             ELSE 6 END,
					         ranked.playerName
					""",
			countQuery = """
					SELECT COUNT(*)
					FROM (
						SELECT
							p.player_id AS playerId,
							p.player_name AS playerName,
							t.team_id AS teamId,
							ROW_NUMBER() OVER (
								PARTITION BY p.player_id
								ORDER BY g.actual_game_start_time DESC, g.game_id DESC
							) AS rn
						FROM game_participants gp
						JOIN games g ON gp.game_id = g.game_id
						JOIN leagues l ON g.league_id = l.league_id
						JOIN players p ON gp.player_id = p.player_id
						JOIN teams t ON gp.team_id = t.team_id
						WHERE l.league_name = :leagueName
						  AND l.season_year = :year
						UNION ALL
						SELECT
							p.player_id AS playerId,
							p.player_name AS playerName,
							NULL AS teamId,
							1 AS rn
						FROM players p
						JOIN player_riot_account pra ON pra.player_id = p.player_id
						WHERE pra.enabled = true
						  AND pra.primary_account = true
						  AND pra.platform = 'KR'
						  AND NOT EXISTS (
							  SELECT 1 FROM game_participants gp2
							  JOIN games g2 ON gp2.game_id = g2.game_id
							  JOIN leagues l2 ON g2.league_id = l2.league_id
							  WHERE gp2.player_id = p.player_id
							    AND l2.league_name = :leagueName
							    AND l2.season_year = :year
						  )
					) ranked
					WHERE ranked.rn = 1
					  AND (:teamId IS NULL OR ranked.teamId = :teamId)
					  AND (:query IS NULL OR LOWER(ranked.playerName) LIKE LOWER(CONCAT('%', :query, '%')))
					""",
			nativeQuery = true)
	Page<LckPlayerOption> findLckPlayerOptions(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("teamId") Long teamId,
			@Param("query") String query,
			Pageable pageable);

	@Query("""
			SELECT DISTINCT
				p.id AS playerId,
				p.name AS playerName,
				p.imageUrl AS playerImageUrl,
				p.role AS role,
				t.id AS teamId,
				t.code AS teamCode,
				t.name AS teamName,
				t.imageUrl AS teamImageUrl
			FROM GameParticipant gp
			JOIN gp.player p
			JOIN gp.team t
			JOIN gp.game g
			JOIN g.league l
			WHERE l.leagueName = :leagueName
			  AND l.seasonYear = :year
			  AND g.actualGameStartTime = (
				  SELECT MAX(g2.actualGameStartTime)
				  FROM GameParticipant gp2
				  JOIN gp2.game g2
				  JOIN g2.league l2
				  WHERE gp2.player = p
				    AND l2.leagueName = :leagueName
				    AND l2.seasonYear = :year
			  )
			  AND p.id = :playerId
			ORDER BY t.name
			""")
	List<LckPlayerOption> findLckPlayerOption(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("playerId") Long playerId);

	@Query("""
			SELECT DISTINCT
				p.id AS playerId,
				p.name AS playerName,
				p.imageUrl AS playerImageUrl,
				p.role AS role,
				t.id AS teamId,
				t.code AS teamCode,
				t.name AS teamName,
				t.imageUrl AS teamImageUrl
			FROM GameParticipant gp
			JOIN gp.player p
			JOIN gp.team t
			JOIN gp.game g
			JOIN g.league l
			WHERE l.leagueName = :leagueName
			  AND l.seasonYear = :year
			  AND g.actualGameStartTime = (
				  SELECT MAX(g2.actualGameStartTime)
				  FROM GameParticipant gp2
				  JOIN gp2.game g2
				  JOIN g2.league l2
				  WHERE gp2.player = p
				    AND l2.leagueName = :leagueName
				    AND l2.seasonYear = :year
			  )
			  AND p.id IN :playerIds
			ORDER BY p.name, t.name
			""")
	List<LckPlayerOption> findLckPlayerOptionsByPlayerIds(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("playerIds") Set<Long> playerIds);

	// 솔랭 전용 선수(계정 보유, 팀 없음)를 LckPlayerOption으로 투영. 구독 표시/검증 병합용.
	@Query(value = """
			SELECT p.player_id AS playerId,
			       p.player_name AS playerName,
			       p.image_url AS playerImageUrl,
			       p.role AS role,
			       NULL AS teamId,
			       NULL AS teamCode,
			       NULL AS teamName,
			       NULL AS teamImageUrl
			FROM players p
			JOIN player_riot_account pra ON pra.player_id = p.player_id
			WHERE pra.enabled = true
			  AND pra.primary_account = true
			  AND pra.platform = 'KR'
			  AND p.player_id IN (:playerIds)
			""", nativeQuery = true)
	List<LckPlayerOption> findSoloRankPlayerOptionsByPlayerIds(@Param("playerIds") Set<Long> playerIds);

	interface LckPlayerOption {
		Long getPlayerId();

		String getPlayerName();

		String getPlayerImageUrl();

		String getRole();

		Long getTeamId();

		String getTeamCode();

		String getTeamName();

		String getTeamImageUrl();
	}
}
