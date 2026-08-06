package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.LckTeamCatalog;
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

	// 로스터 diff 알림 대상: 소속팀이 LCK 1군인 선수. 팀명 비교를 위해 currentTeam을 함께 로딩한다.
	@EntityGraph(attributePaths = {"currentTeam"})
	@Query("SELECT p FROM Player p WHERE UPPER(p.currentTeam.code) IN :codes")
	List<Player> findByCurrentTeamCodeIn(@Param("codes") List<String> codes);

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
	 *
	 * <p>UNION ALL 분기(해당 리그 미출전 + 계정 보유)에는 플랫폼 조건을 걸지 않는다. 예전엔
	 * {@code platform = 'KR'}이 있어 해외 리그 선수의 NA/EUW 계정이 목록에서 빠졌다
	 * (라이브 감지는 플랫폼 무관인데 구독 자체가 불가능한 모순). 자격은 {@code enabled}·
	 * {@code primary_account}만으로 판단한다.
	 *
	 * <p>{@code current_team_id}(백오피스 수동 소속팀)가 <b>{@code team_code}를 가진 팀일 때만</b> 경기
	 * 기록 팀을 덮는다. 이적한 선수는 새 팀 경기가 CSV에 들어오기 전까지 옛 팀에 남는데, 백오피스에서
	 * 고치면 즉시 반영되게 하려는 것이다. 조건을 다는 이유: {@code current_team_id}는 V54가 "전 리그
	 * 최근 경기 팀"으로 일괄 백필한 값이라 1군 선수인데도 챌린저스·아카데미 팀을 가리키는 경우가 많다
	 * (2026-08 기준 LCK 2026 출전자 64명 중 9명). 2군 팀은 {@code team_code}가 없어 온보딩 팀 목록
	 * ({@link LckTeamCatalog#TEAM_CODES})에 없으므로, 조건 없이 덮으면 그 9명이 어느 팀 목록에도
	 * 안 잡혀 사라진다.
	 *
	 * <p>예전엔 이 조건이 {@code team_code IN (LCK 1군 10개)}였다. 그러면 LCK 출전 이력이 있고 지금은
	 * 해외 1군에 있는 선수(예: Loki — LCK 2026 → Cloud9)의 소속팀을 백오피스에서 고쳐도 앱에는 옛 LCK
	 * 팀으로 계속 보였다. 2026-08-04 프로덕션 실측으로 챌린저스·아카데미 팀 41개는 전부
	 * {@code team_code}가 NULL이고, LCK 2026 출전자 중 코드 있는 비-LCK 팀을 가리키는 선수는 0명이라
	 * NULL 여부만으로 좁혀도 원래 목적(2군 백필 오염 차단)이 유지된다.
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
							COALESCE(ct.team_id, t.team_id) AS teamId,
							COALESCE(ct.team_code, t.team_code) AS teamCode,
							COALESCE(ct.team_name, t.team_name) AS teamName,
							COALESCE(ct.team_image_url, t.team_image_url) AS teamImageUrl,
							ROW_NUMBER() OVER (
								PARTITION BY p.player_id
								ORDER BY g.actual_game_start_time DESC, g.game_id DESC
							) AS rn
						FROM game_participants gp
						JOIN games g ON gp.game_id = g.game_id
						JOIN leagues l ON g.league_id = l.league_id
						JOIN players p ON gp.player_id = p.player_id
						JOIN teams t ON gp.team_id = t.team_id
						LEFT JOIN teams ct ON ct.team_id = p.current_team_id
						                  AND ct.team_code IS NOT NULL
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
							COALESCE(ct.team_id, t.team_id) AS teamId,
							ROW_NUMBER() OVER (
								PARTITION BY p.player_id
								ORDER BY g.actual_game_start_time DESC, g.game_id DESC
							) AS rn
						FROM game_participants gp
						JOIN games g ON gp.game_id = g.game_id
						JOIN leagues l ON g.league_id = l.league_id
						JOIN players p ON gp.player_id = p.player_id
						JOIN teams t ON gp.team_id = t.team_id
						LEFT JOIN teams ct ON ct.team_id = p.current_team_id
						                  AND ct.team_code IS NOT NULL
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

	// 팀 컬럼 override 근거는 findLckPlayerOptions javadoc 참고(team_code가 있는 팀일 때만 current_team이 이긴다).
	@Query("""
			SELECT DISTINCT
				p.id AS playerId,
				p.name AS playerName,
				p.imageUrl AS playerImageUrl,
				p.role AS role,
				COALESCE(ct.id, t.id) AS teamId,
				COALESCE(ct.code, t.code) AS teamCode,
				COALESCE(ct.name, t.name) AS teamName,
				COALESCE(ct.imageUrl, t.imageUrl) AS teamImageUrl
			FROM GameParticipant gp
			JOIN gp.player p
			JOIN gp.team t
			JOIN gp.game g
			JOIN g.league l
			LEFT JOIN Team ct ON ct.id = p.currentTeam.id AND ct.code IS NOT NULL
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
			ORDER BY COALESCE(ct.name, t.name)
			""")
	List<LckPlayerOption> findLckPlayerOption(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("playerId") Long playerId);

	// 팀 컬럼 override 근거는 findLckPlayerOptions javadoc 참고(team_code가 있는 팀일 때만 current_team이 이긴다).
	@Query("""
			SELECT DISTINCT
				p.id AS playerId,
				p.name AS playerName,
				p.imageUrl AS playerImageUrl,
				p.role AS role,
				COALESCE(ct.id, t.id) AS teamId,
				COALESCE(ct.code, t.code) AS teamCode,
				COALESCE(ct.name, t.name) AS teamName,
				COALESCE(ct.imageUrl, t.imageUrl) AS teamImageUrl
			FROM GameParticipant gp
			JOIN gp.player p
			JOIN gp.team t
			JOIN gp.game g
			JOIN g.league l
			LEFT JOIN Team ct ON ct.id = p.currentTeam.id AND ct.code IS NOT NULL
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
			ORDER BY p.name, COALESCE(ct.name, t.name)
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

	// 백오피스 구독 탭: 구독 가능한 선수(솔랭 계정 보유 ∪ LCK 2026 출전) + 구독자 수. 인기순(구독자 수 desc) 정렬.
	// platform 무관(KR/EUW1/NA1 모두) — 해외 이적 솔랭 선수도 포함.
	// 구독 가능 집합을 UNION 서브셋(LCK 2026 출전 ∪ 솔랭 계정)으로 1회 구체화 후 JOIN.
	// 선수 전체(3900+)에 상관 EXISTS를 돌리면 DEPENDENT SUBQUERY가 행마다 실행돼 prod에서 4초+ 걸림 → 서브셋 조인으로 해소.
	@Query(value = """
			SELECT p.player_id AS playerId,
			       p.player_name AS playerName,
			       p.image_url AS imageUrl,
			       p.role AS role,
			       t.team_id AS teamId,
			       t.team_name AS teamName,
			       pra.riot_id AS riotId,
			       pra.platform AS platform,
			       COUNT(mfp.id) AS subscriberCount
			FROM players p
			JOIN (
			        SELECT gp.player_id FROM game_participants gp
			          JOIN games g ON g.game_id = gp.game_id
			          JOIN leagues l ON l.league_id = g.league_id
			          WHERE l.league_name = 'LCK' AND l.season_year = 2026
			        UNION
			        SELECT pra2.player_id FROM player_riot_account pra2
			          WHERE pra2.enabled = true AND pra2.primary_account = true
			     ) sub ON sub.player_id = p.player_id
			LEFT JOIN teams t ON t.team_id = p.current_team_id
			LEFT JOIN player_riot_account pra
			       ON pra.player_id = p.player_id AND pra.enabled = true AND pra.primary_account = true
			LEFT JOIN member_favorite_player mfp ON mfp.player_id = p.player_id
			WHERE (:q IS NULL OR LOWER(p.player_name) LIKE LOWER(CONCAT('%', :q, '%')))
			GROUP BY p.player_id, p.player_name, p.image_url, p.role, t.team_id, t.team_name, pra.riot_id, pra.platform
			ORDER BY subscriberCount DESC, p.player_name ASC
			""",
			countQuery = """
			SELECT COUNT(*) FROM players p
			JOIN (
			        SELECT gp.player_id FROM game_participants gp
			          JOIN games g ON g.game_id = gp.game_id
			          JOIN leagues l ON l.league_id = g.league_id
			          WHERE l.league_name = 'LCK' AND l.season_year = 2026
			        UNION
			        SELECT pra2.player_id FROM player_riot_account pra2
			          WHERE pra2.enabled = true AND pra2.primary_account = true
			     ) sub ON sub.player_id = p.player_id
			WHERE (:q IS NULL OR LOWER(p.player_name) LIKE LOWER(CONCAT('%', :q, '%')))
			""",
			nativeQuery = true)
	Page<SubscribablePlayerView> findSubscribablePlayers(@Param("q") String q, Pageable pageable);

	interface SubscribablePlayerView {
		Long getPlayerId();

		String getPlayerName();

		String getImageUrl();

		String getRole();

		Long getTeamId();

		String getTeamName();

		String getRiotId();

		String getPlatform();

		long getSubscriberCount();
	}
}
