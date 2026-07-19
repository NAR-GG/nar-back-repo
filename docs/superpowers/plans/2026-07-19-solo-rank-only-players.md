# 솔랭 전용 선수 지원 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** LCK 출전 기록이 없는 은퇴/비현역 선수(Deft, Rascal 등)를 솔로 랭크 알림 대상으로 등록·감시·구독할 수 있게 한다.

**Architecture:** 신규 상태 컬럼 없이 `enabled+primary+KR PlayerRiotAccount` 존재를 "솔랭 추적 마커"로 재사용한다. (1) Riot 계정 sync 스코프를 "리그 출전 ∪ 계정 보유"로 확장하고, (2) 솔랭 전용 선수를 만드는 admin 엔드포인트를 추가하며, (3) 구독 자격을 "LCK 2026 출전 OR 계정 보유"로 확장한다.

**Tech Stack:** Spring Boot 3.5.3, Java 17, JPA/Hibernate, MySQL(운영)/H2(DataJpaTest), JUnit5 + Mockito + AssertJ.

## Global Constraints

- 모든 서술형 산출물(주석·커밋·문서)은 한국어.
- `main` 직접 수정 금지. 현재 브랜치 `feat/solo-rank-only-players`에서 작업, `main` 타깃 PR.
- 커밋 메시지 말미: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
- 기존 컨벤션 준수: 생성자 주입(`@RequiredArgsConstructor`), 커스텀 예외/에러 매핑은 `BackofficeController`의 기존 `@ExceptionHandler` 재사용.
- 구독 자격 규칙(3곳 공통): `구독가능(player) = LCK 2026 출전 OR (enabled=true AND primary_account=true AND platform='KR' 인 PlayerRiotAccount 보유)`.
- `PlayerRiotAccount` 테이블: `player_riot_account`, 컬럼 `player_id, riot_id, game_name, tag_line, platform, puuid, primary_account, enabled`.
- 리그/시즌 상수: `LEAGUE_NAME="LCK"`, `CURRENT_SEASON_YEAR=2026`.

---

### Task 1: Riot 계정 sync 스코프 확장

목적: 솔랭 전용 선수(리그 출전 없음, 계정만 보유)의 puuid가 매일 06:00 sync로 유지되게 한다.

**Files:**
- Modify: `src/main/java/com/toy/nar/domain/participant/repository/PlayerRepository.java` (line 60-65 아래에 메서드 추가)
- Modify: `src/main/java/com/toy/nar/app/riot/PlayerRiotAccountSyncService.java:42`
- Modify: `src/test/java/com/toy/nar/app/riot/PlayerRiotAccountSyncServiceTest.java:61` (모의 대상 메서드명 교체)
- Test: `src/test/java/com/toy/nar/domain/participant/repository/PlayerRepositorySoloRankSyncTargetsTest.java` (신규)

**Interfaces:**
- Produces: `List<Player> PlayerRepository.findSoloRankSyncTargets(String leagueName)`

- [ ] **Step 1: 리포지토리 테스트 작성 (신규 파일)**

`PlayerRepositoryLckParticipationTest.java`의 DataJpaTest 설정을 참고해 작성한다. H2 create-drop 스키마 + 네이티브 시딩.

```java
package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import com.toy.nar.domain.participant.entity.Player;

@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class PlayerRepositorySoloRankSyncTargetsTest {

	@Autowired
	private PlayerRepository playerRepository;

	@PersistenceContext
	private EntityManager em;

	@BeforeEach
	void seed() {
		exec("INSERT INTO leagues (league_id, league_name, season_year) VALUES (100, 'LCK', 2026)");
		exec("INSERT INTO teams (team_id, team_name, team_code) VALUES (10, 'T1', 'T1')");
		// 1: LCK 출전자, 2: 계정만 보유(비출전), 3: 무관(제외)
		exec("INSERT INTO players (player_id, player_name, image_locked, game_accounts_locked)"
				+ " VALUES (1, 'Faker', false, false)");
		exec("INSERT INTO players (player_id, player_name, image_locked, game_accounts_locked)"
				+ " VALUES (2, 'Deft', false, true)");
		exec("INSERT INTO players (player_id, player_name, image_locked, game_accounts_locked)"
				+ " VALUES (3, 'NoOne', false, false)");
		exec("INSERT INTO games (game_id, league_id, actual_game_start_time)"
				+ " VALUES (1, 100, '2026-01-10 10:00:00')");
		exec("INSERT INTO game_participants (id, game_id, player_id, team_id) VALUES (1, 1, 1, 10)");
		exec("INSERT INTO player_riot_account"
				+ " (id, player_id, riot_id, game_name, tag_line, platform, puuid, primary_account, enabled)"
				+ " VALUES (1, 2, 'Deft#8366', 'Deft', '8366', 'KR', 'puuid-deft', true, true)");
	}

	@Test
	void includesLeaguePlayersAndAccountHolders() {
		List<Player> targets = playerRepository.findSoloRankSyncTargets("LCK");
		assertThat(targets).extracting(Player::getName)
				.containsExactlyInAnyOrder("Faker", "Deft");
	}

	private void exec(String sql) {
		em.createNativeQuery(sql).executeUpdate();
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.toy.nar.domain.participant.repository.PlayerRepositorySoloRankSyncTargetsTest"`
Expected: 컴파일 실패 — `findSoloRankSyncTargets` 메서드 없음.

- [ ] **Step 3: 리포지토리 메서드 추가**

`PlayerRepository.java`의 `findPlayersByLeagueName`(line 65) 바로 아래에 추가:

```java
	// 솔랭 계정 sync 대상: 해당 리그 출전자 ∪ PlayerRiotAccount 보유자(비출전 은퇴 선수 포함).
	// OR-EXISTS라 semijoin이 안 되지만, 하루 1회 배치이고 대상 수가 적어 성능 영향 없다.
	@Query("""
			SELECT DISTINCT p FROM Player p
			WHERE EXISTS (SELECT 1 FROM GameParticipant gp
			              WHERE gp.player = p AND gp.game.league.leagueName = :leagueName)
			   OR EXISTS (SELECT 1 FROM PlayerRiotAccount pra WHERE pra.player = p)
			""")
	List<Player> findSoloRankSyncTargets(@Param("leagueName") String leagueName);
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.toy.nar.domain.participant.repository.PlayerRepositorySoloRankSyncTargetsTest"`
Expected: PASS

- [ ] **Step 5: sync 서비스가 새 메서드를 쓰도록 교체**

`PlayerRiotAccountSyncService.java:42` 변경:

```java
		List<Player> players = playerRepository.findSoloRankSyncTargets(riotMonitorProperties.getTargetLeague());
```

- [ ] **Step 6: 기존 sync 서비스 테스트 모의 대상 교체**

`PlayerRiotAccountSyncServiceTest.java:61` 변경:

```java
		when(playerRepository.findSoloRankSyncTargets("LCK")).thenReturn(List.of(player));
```

그리고 같은 파일에서 `findPlayersByLeagueName`를 검증(`verify`)하는 라인(88행 부근)이 있으면 `findSoloRankSyncTargets`로 함께 교체한다.

- [ ] **Step 7: sync 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "com.toy.nar.app.riot.PlayerRiotAccountSyncServiceTest"`
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/toy/nar/domain/participant/repository/PlayerRepository.java \
        src/main/java/com/toy/nar/app/riot/PlayerRiotAccountSyncService.java \
        src/test/java/com/toy/nar/app/riot/PlayerRiotAccountSyncServiceTest.java \
        src/test/java/com/toy/nar/domain/participant/repository/PlayerRepositorySoloRankSyncTargetsTest.java
git commit -m "feat: Riot 계정 sync 대상에 계정 보유 비출전 선수 포함

리그 출전 기록이 없어도 PlayerRiotAccount가 있으면 매일 sync 대상에
포함해 puuid를 유지한다. 솔랭 전용 선수(은퇴 프로) 감시의 전제.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 솔랭 전용 선수 등록 admin 엔드포인트

목적: LCK 출전 이력 없는 선수를 이름+riotId로 등록 → Player 생성 + Riot 검증 + PlayerRiotAccount 생성.

**주의(트랜잭션):** 기존 `syncPlayerAccountNow`는 `PROPAGATION_REQUIRES_NEW`로 별도 트랜잭션을 열어 계정을 저장한다. 신규 선수는 아직 커밋 전이라 REQUIRES_NEW 트랜잭션에서 player 행이 보이지 않아 FK 삽입이 실패한다. 따라서 등록 경로는 **현재 트랜잭션에 참여**하는 별도 메서드로 계정을 저장하고, Riot 404 시 player 삽입까지 함께 롤백한다.

**Files:**
- Modify: `src/main/java/com/toy/nar/app/riot/PlayerRiotAccountSyncService.java` (line 76-118: 공통 로직 추출 + 신규 public 메서드)
- Modify: `src/main/java/com/toy/nar/app/participant/service/PlayerAdminService.java`
- Modify: `src/main/java/com/toy/nar/api/admin/BackofficeController.java` (POST 매핑 + 요청 record)
- Test: `src/test/java/com/toy/nar/app/participant/service/PlayerAdminServiceTest.java` (케이스 추가)

**Interfaces:**
- Produces: `void PlayerRiotAccountSyncService.resolveAndSaveInCurrentTransaction(Player player, String riotId)`
- Produces: `Player PlayerAdminService.createSoloRankPlayer(String name, String imageUrl, String riotId)`
- Produces: `POST /api/admin/players/solo-rank` body `SoloRankPlayerCreateRequest(String name, String imageUrl, String riotId)`

- [ ] **Step 1: sync 서비스 공통 로직 추출 (리팩터, 동작 불변)**

`PlayerRiotAccountSyncService.java`의 `syncSinglePlayerAccount`(line 76-111) 내부 본문을 `resolveAndPersist(Player, String riotId)`로 추출하고, 기존 메서드는 REQUIRES_NEW 래퍼만 남긴다. `candidate.riotId()` 대신 원시 riotId 문자열을 인자로 받는다.

```java
	private void syncSinglePlayerAccount(Player player, PrimaryAccountCandidate candidate) {
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
		transactionTemplate.executeWithoutResult(status -> resolveAndPersist(player, candidate.riotId()));
	}

	// 현재 트랜잭션에 참여해 Riot ID를 검증·해석하고 PlayerRiotAccount를 저장한다.
	// 신규 선수 등록처럼 player가 아직 커밋 전인 경우 REQUIRES_NEW를 쓰면 FK가 안 보이므로 이 경로를 쓴다.
	public void resolveAndSaveInCurrentTransaction(Player player, String riotId) {
		riotApiClient.assertConfigured();
		resolveAndPersist(player, riotId);
	}

	private void resolveAndPersist(Player player, String riotId) {
		RiotIdParser.ParsedRiotId parsedRiotId = RiotIdParser.parse(riotId)
				.orElseThrow(() -> new IllegalArgumentException("Invalid Riot ID: " + riotId));

		RiotAccountResolveResponse accountResponse = riotApiClient.resolveAccountByRiotId(
				parsedRiotId.gameName(),
				parsedRiotId.tagLine());

		PlayerRiotAccount playerRiotAccount = playerRiotAccountRepository.findByPlayerId(player.getId())
				.orElseGet(() -> PlayerRiotAccount.builder()
						.player(player)
						.riotId(parsedRiotId.normalizedRiotId())
						.gameName(accountResponse.gameName())
						.tagLine(accountResponse.tagLine())
						.platform(KR_PLATFORM)
						.puuid(accountResponse.puuid())
						.summonerId("")
						.primaryAccount(true)
						.enabled(true)
						.liveStatus(PlayerRiotAccountLiveStatus.OFFLINE)
						.build());

		playerRiotAccount.updateResolvedAccount(
				parsedRiotId.normalizedRiotId(),
				accountResponse.gameName(),
				accountResponse.tagLine(),
				KR_PLATFORM,
				accountResponse.puuid());
		playerRiotAccount.markPrimaryAccount(true);
		playerRiotAccount.setEnabled(true);
		playerRiotAccountRepository.saveAndFlush(playerRiotAccount);
	}
```

`syncPlayerAccountNow`(line 115-118)는 그대로 둔다.

- [ ] **Step 2: 리팩터 회귀 확인**

Run: `./gradlew test --tests "com.toy.nar.app.riot.PlayerRiotAccountSyncServiceTest"`
Expected: PASS (동작 불변)

- [ ] **Step 3: PlayerAdminService 테스트 작성 (케이스 추가)**

`PlayerAdminServiceTest.java`에 추가. 기존 `@Mock` 필드(`playerRepository`, `playerRiotAccountSyncService`) 재사용.

```java
	@Test
	@DisplayName("솔랭 전용 선수 생성: Player 저장 + 계정 해석 호출")
	void createsSoloRankPlayer() {
		when(playerRepository.findByName("Deft")).thenReturn(java.util.Optional.empty());
		when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

		Player created = service.createSoloRankPlayer("Deft", null, "Deft#8366");

		assertThat(created.getName()).isEqualTo("Deft");
		assertThat(created.getGameAccounts()).contains("Deft#8366");
		assertThat(created.isGameAccountsLocked()).isTrue();
		verify(playerRiotAccountSyncService).resolveAndSaveInCurrentTransaction(created, "Deft#8366");
	}

	@Test
	@DisplayName("중복 이름이면 IllegalStateException")
	void rejectsDuplicateName() {
		when(playerRepository.findByName("Deft")).thenReturn(java.util.Optional.of(new Player("Deft", null)));

		assertThatThrownBy(() -> service.createSoloRankPlayer("Deft", null, "Deft#8366"))
				.isInstanceOf(IllegalStateException.class);
		verify(playerRepository, never()).save(any());
	}

	@Test
	@DisplayName("riotId 형식 오류면 IllegalArgumentException")
	void rejectsInvalidSoloRankRiotId() {
		when(playerRepository.findByName("Deft")).thenReturn(java.util.Optional.empty());
		when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

		assertThatThrownBy(() -> service.createSoloRankPlayer("Deft", null, "NoHashTag"))
				.isInstanceOf(IllegalArgumentException.class);
		verify(playerRiotAccountSyncService, never()).resolveAndSaveInCurrentTransaction(any(), any());
	}
```

필요 import: `static org.assertj.core.api.Assertions.assertThatThrownBy;`, `static org.mockito.ArgumentMatchers.any;`, `static org.mockito.Mockito.never;`, `static org.mockito.Mockito.verify;`, `static org.mockito.Mockito.when;`, `org.junit.jupiter.api.DisplayName;` (대부분 이미 존재 — 없는 것만 추가).

- [ ] **Step 4: 테스트 실패 확인**

Run: `./gradlew test --tests "com.toy.nar.app.participant.service.PlayerAdminServiceTest"`
Expected: 컴파일 실패 — `createSoloRankPlayer` 없음.

- [ ] **Step 5: PlayerAdminService에 createSoloRankPlayer 구현**

`PlayerAdminService.java`에 메서드 추가(기존 `serializeGameAccounts` 재사용):

```java
	// 솔랭 전용 선수 등록: LCK 출전 이력 없이도 생성 가능(update 경로와 달리 참여 검증 안 함).
	// Riot 404 등 예외는 트랜잭션 롤백 → Player 삽입도 취소된다.
	@Transactional
	public Player createSoloRankPlayer(String name, String imageUrl, String riotId) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("선수명은 비울 수 없습니다");
		}
		if (playerRepository.findByName(name).isPresent()) {
			throw new IllegalStateException("이미 존재하는 선수입니다: " + name);
		}
		Player player = playerRepository.save(Player.builder()
				.name(name.trim())
				.imageUrl(imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim())
				.build());
		String json = serializeGameAccounts(List.of(
				new BackofficeController.GameAccountEntry("KR", riotId, null)));
		player.overrideGameAccounts(json);
		playerRiotAccountSyncService.resolveAndSaveInCurrentTransaction(player, riotId);
		return player;
	}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests "com.toy.nar.app.participant.service.PlayerAdminServiceTest"`
Expected: PASS

- [ ] **Step 7: 컨트롤러 엔드포인트 + 요청 record 추가**

`BackofficeController.java`의 `updatePlayer`(line 112-117) 아래에 추가:

```java
    // 솔랭 전용 선수 등록(은퇴/비현역). LCK 출전 이력 없이 이름+riotId로 생성.
    @PostMapping("/players/solo-rank")
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerRow createSoloRankPlayer(@RequestBody SoloRankPlayerCreateRequest request) {
        return PlayerRow.from(playerAdminService.createSoloRankPlayer(
                request.name(), request.imageUrl(), request.riotId()));
    }
```

record 정의부(line 196 `PlayerUpdateRequest` 아래)에 추가:

```java
    public record SoloRankPlayerCreateRequest(String name, String imageUrl, String riotId) {}
```

`@PostMapping`, `@ResponseStatus`, `HttpStatus` import가 없으면 추가. 오류 매핑은 기존 `@ExceptionHandler`가 처리(IllegalState/IllegalArgument→400, RiotApiException→404/502).

- [ ] **Step 8: 전체 빌드 확인**

Run: `./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/toy/nar/app/riot/PlayerRiotAccountSyncService.java \
        src/main/java/com/toy/nar/app/participant/service/PlayerAdminService.java \
        src/main/java/com/toy/nar/api/admin/BackofficeController.java \
        src/test/java/com/toy/nar/app/participant/service/PlayerAdminServiceTest.java
git commit -m "feat: 솔랭 전용 선수 등록 엔드포인트 추가

POST /api/admin/players/solo-rank — LCK 출전 이력 없이 이름+riotId로
선수를 만들고 Riot API로 검증해 PlayerRiotAccount를 생성한다. 신규 선수는
현재 트랜잭션에 참여해 계정을 저장(REQUIRES_NEW 미가시성 회피)하고,
Riot 오류 시 Player 삽입까지 롤백한다.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 구독 검색 목록에 솔랭 전용 선수 노출

목적: `getAvailablePlayers`가 계정 보유(2026 LCK 미출전) 선수도 페이지에 포함하게 한다.

**Files:**
- Modify: `src/main/java/com/toy/nar/domain/participant/repository/PlayerRepository.java:95-161` (`findLckPlayerOptions` 쿼리+countQuery)
- Test: `src/test/java/com/toy/nar/domain/participant/repository/PlayerRepositoryLckPlayerOptionsTest.java` (케이스 추가)

**Interfaces:**
- 시그니처 불변. `findLckPlayerOptions(leagueName, year, teamId, query, Pageable)` 결과 집합만 확장.

- [ ] **Step 1: 테스트 케이스 추가**

기존 `PlayerRepositoryLckPlayerOptionsTest`의 `seed()`에 솔랭 전용 선수 1명 추가하고 검증 테스트를 붙인다.

`seed()` 끝부분에 추가:

```java
		// 솔랭 전용: LCK 미출전 + KR 주계정 enabled → 목록에 팀 없이 노출돼야 한다.
		exec(player(9, "Deft", "Bot"));
		exec("INSERT INTO player_riot_account"
				+ " (id, player_id, riot_id, game_name, tag_line, platform, puuid, primary_account, enabled)"
				+ " VALUES (99, 9, 'Deft#8366', 'Deft', '8366', 'KR', 'puuid-deft', true, true)");
```

신규 테스트 메서드:

```java
	@Test
	@DisplayName("솔랭 전용 선수(계정 보유, LCK 미출전)도 팀 없이 목록에 포함된다")
	void includesSoloRankOnlyPlayer() {
		Page<LckPlayerOption> page = playerRepository.findLckPlayerOptions(
				LCK, YEAR, null, null, PageRequest.of(0, 50));

		LckPlayerOption deft = page.getContent().stream()
				.filter(o -> "Deft".equals(o.getPlayerName()))
				.findFirst()
				.orElseThrow();
		assertThat(deft.getTeamId()).isNull();
		assertThat(deft.getTeamName()).isNull();
	}

	@Test
	@DisplayName("teamId 필터가 있으면 팀 없는 솔랭 전용 선수는 제외된다")
	void excludesSoloRankOnlyPlayerWhenTeamFilterSet() {
		Page<LckPlayerOption> page = playerRepository.findLckPlayerOptions(
				LCK, YEAR, T1, null, PageRequest.of(0, 50));

		assertThat(page.getContent()).extracting(LckPlayerOption::getPlayerName)
				.doesNotContain("Deft");
	}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.toy.nar.domain.participant.repository.PlayerRepositoryLckPlayerOptionsTest"`
Expected: `includesSoloRankOnlyPlayer` FAIL (Deft 없음 → orElseThrow).

- [ ] **Step 3: findLckPlayerOptions 쿼리에 UNION ALL 브랜치 추가**

`value` 쿼리의 파생 테이블 `FROM ( ... ) ranked` 안, 기존 `ROW_NUMBER` SELECT 뒤에 `UNION ALL` 브랜치를 넣는다. 최종 형태:

```java
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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.toy.nar.domain.participant.repository.PlayerRepositoryLckPlayerOptionsTest"`
Expected: PASS (신규 2개 + 기존 케이스 모두)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/toy/nar/domain/participant/repository/PlayerRepository.java \
        src/test/java/com/toy/nar/domain/participant/repository/PlayerRepositoryLckPlayerOptionsTest.java
git commit -m "feat: 구독 검색 목록에 계정 보유 비출전 선수 포함

findLckPlayerOptions에 UNION ALL 브랜치를 추가해 enabled+primary KR
계정을 가진 LCK 2026 미출전 선수를 팀 없이 노출한다. 팀 필터 지정 시
자연히 제외된다. 페이지네이션·카운트는 SQL에서 그대로 처리.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: 구독 추가·표시에서 솔랭 전용 선수 허용

목적: `subscribe`가 솔랭 전용 선수를 거절하지 않고, `getSubscriptions`가 이들을 목록에 표시하게 한다.

**Files:**
- Modify: `src/main/java/com/toy/nar/domain/participant/repository/PlayerRepository.java` (신규 메서드 추가)
- Modify: `src/main/java/com/toy/nar/app/mobile/subscription/MobilePlayerSubscriptionService.java`
- Test: `src/test/java/com/toy/nar/app/mobile/subscription/MobilePlayerSubscriptionServiceTest.java` (케이스 추가)

**Interfaces:**
- Consumes: `PlayerRepository.LckPlayerOption` (기존 프로젝션)
- Produces: `List<LckPlayerOption> PlayerRepository.findSoloRankPlayerOptionsByPlayerIds(Set<Long> playerIds)`

- [ ] **Step 1: 리포지토리 메서드 추가 (네이티브)**

`PlayerRepository.java`의 `findLckPlayerOptionsByPlayerIds`(line 226 부근) 아래에 추가:

```java
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
```

- [ ] **Step 2: 서비스 테스트 케이스 추가**

`MobilePlayerSubscriptionServiceTest.java`에 추가. 기존 헬퍼(`member`, `player`, `option`) 재사용.

```java
	@Test
	@DisplayName("솔랭 전용 구독 선수(2026 LCK 미출전)도 getSubscriptions에 표시된다")
	void showsSoloRankOnlySubscription() {
		Member member = member(7L);
		Player deft = player(9L, "Deft");
		MemberFavoritePlayer sub = MemberFavoritePlayer.builder().member(member).player(deft).build();
		PlayerRepository.LckPlayerOption soloOption = option(9L, "Deft", null, null, null);

		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(subscriptionRepository.findAllByMember_Id(7L)).thenReturn(List.of(sub));
		when(playerRepository.findLckPlayerOptionsByPlayerIds("LCK", 2026, Set.of(9L)))
				.thenReturn(List.of());
		when(playerRepository.findSoloRankPlayerOptionsByPlayerIds(Set.of(9L)))
				.thenReturn(List.of(soloOption));

		List<PlayerSubscriptionResponse> response = service.getSubscriptions(7L);

		assertThat(response).extracting(PlayerSubscriptionResponse::playerName).containsExactly("Deft");
	}

	@Test
	@DisplayName("subscribe: LCK 옵션 없어도 솔랭 전용 옵션이 있으면 구독 성공")
	void subscribesSoloRankOnlyPlayer() {
		Member member = member(7L);
		Player deft = player(9L, "Deft");
		PlayerRepository.LckPlayerOption soloOption = option(9L, "Deft", null, null, null);

		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(playerRepository.findById(9L)).thenReturn(Optional.of(deft));
		when(playerRepository.findLckPlayerOption("LCK", 2026, 9L)).thenReturn(List.of());
		when(playerRepository.findSoloRankPlayerOptionsByPlayerIds(Set.of(9L)))
				.thenReturn(List.of(soloOption));
		when(subscriptionRepository.findByMember_IdAndPlayer_Id(7L, 9L)).thenReturn(Optional.empty());

		PlayerSubscriptionResponse response = service.subscribe(7L, 9L);

		assertThat(response.playerName()).isEqualTo("Deft");
		verify(subscriptionRepository).save(any(MemberFavoritePlayer.class));
	}

	@Test
	@DisplayName("subscribe: LCK·솔랭 둘 다 아니면 400")
	void rejectsNonEligiblePlayer() {
		Member member = member(7L);
		Player nobody = player(9L, "Nobody");

		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(playerRepository.findById(9L)).thenReturn(Optional.of(nobody));
		when(playerRepository.findLckPlayerOption("LCK", 2026, 9L)).thenReturn(List.of());
		when(playerRepository.findSoloRankPlayerOptionsByPlayerIds(Set.of(9L))).thenReturn(List.of());

		assertThatThrownBy(() -> service.subscribe(7L, 9L))
				.isInstanceOf(ResponseStatusException.class);
	}
```

주: 기존 `option(...)` 헬퍼는 `teamCode.toLowerCase()`를 호출하므로 null teamCode에서 NPE가 난다. 헬퍼의 `teamImageUrl` 스텁을 null 가드로 고친다:

```java
		when(option.getTeamImageUrl()).thenReturn(teamCode == null ? null : teamCode.toLowerCase() + ".png");
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "com.toy.nar.app.mobile.subscription.MobilePlayerSubscriptionServiceTest"`
Expected: 컴파일 실패 — `findSoloRankPlayerOptionsByPlayerIds` 모의 불가/미정의.

- [ ] **Step 4: 서비스 getSubscriptions 병합 구현**

`MobilePlayerSubscriptionService.getSubscriptions`(line 39-65)에서 `playerOptions` 맵 구성 직후 솔랭 전용 옵션을 병합한다. `playerOptions` 지역변수를 가변 맵으로 두고 `putIfAbsent` 병합:

```java
		Map<Long, PlayerRepository.LckPlayerOption> playerOptions = playerRepository
				.findLckPlayerOptionsByPlayerIds(LEAGUE_NAME, CURRENT_SEASON_YEAR, playerIds).stream()
				.collect(Collectors.toMap(
						PlayerRepository.LckPlayerOption::getPlayerId,
						option -> option,
						(existing, ignored) -> existing,
						LinkedHashMap::new));
		// LCK 옵션이 없는(은퇴/비출전) 구독 선수는 계정 기준 옵션으로 보완한다.
		playerRepository.findSoloRankPlayerOptionsByPlayerIds(playerIds)
				.forEach(option -> playerOptions.putIfAbsent(option.getPlayerId(), option));
```

- [ ] **Step 5: 서비스 subscribe 자격 확장 구현**

`requireLckPlayer`(line 130-136)를 LCK→솔랭 순서로 조회하도록 교체:

```java
	private PlayerRepository.LckPlayerOption requireLckPlayer(Long playerId) {
		return playerRepository.findLckPlayerOption(LEAGUE_NAME, CURRENT_SEASON_YEAR, playerId).stream()
				.findFirst()
				.or(() -> playerRepository
						.findSoloRankPlayerOptionsByPlayerIds(Set.of(playerId)).stream()
						.findFirst())
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						"구독 가능한 선수가 아닙니다."));
	}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests "com.toy.nar.app.mobile.subscription.MobilePlayerSubscriptionServiceTest"`
Expected: PASS (기존 + 신규 3개)

- [ ] **Step 7: 전체 테스트 확인**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/toy/nar/domain/participant/repository/PlayerRepository.java \
        src/main/java/com/toy/nar/app/mobile/subscription/MobilePlayerSubscriptionService.java \
        src/test/java/com/toy/nar/app/mobile/subscription/MobilePlayerSubscriptionServiceTest.java
git commit -m "feat: 솔랭 전용 선수 구독 추가·표시 허용

subscribe가 LCK 2026 옵션이 없어도 계정 기준 옵션으로 재검증하고,
getSubscriptions가 계정 기준 옵션을 병합해 은퇴/비출전 구독 선수도
목록에 표시한다.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 배포 후 데이터 작업 (코드 아님)

앱 배포 후, 관리자 인증으로 다음 호출:

```
POST /api/admin/players/solo-rank  { "name": "Deft",   "riotId": "Deft#8366" }
POST /api/admin/players/solo-rank  { "name": "Rascal", "riotId": "Rascal#1231" }
```

- Peanut(id 717)은 이미 감시 중 → 조치 불필요. 2026 LCK 미출전이면 Task 3/4 확장으로 자동 구독 가능.
- 등록 직후 다음 06:00 sync부터 puuid 유지, 10초 폴링이 솔랭 감지 시 구독자/토픽에 푸시.

## 롤아웃 확인

- [ ] `POST /players/solo-rank`로 Deft/Rascal 생성 성공(201, PlayerRow 반환)
- [ ] 모바일 `GET /api/mobile/me/player-subscriptions/available-players?query=Deft` 에 노출
- [ ] `POST /api/mobile/me/player-subscriptions {playerId}` 구독 성공
- [ ] `GET /api/mobile/me/player-subscriptions` 에 표시
- [ ] 세 선수가 솔랭 진입 시 푸시 도달(운영에서 관찰)
