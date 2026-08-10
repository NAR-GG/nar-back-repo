# 알림 잠자기(방해금지 시간) 백엔드 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원이 정한 시간대에는 모든 푸시를 소리 없이(알림함에만 쌓이게) 발송한다.

**Architecture:** `member`에 잠자기 시간 컬럼 3개를 두고, 발송 직전에 구독자를 "잠자기 걸린 집합 / 안 걸린 집합" 2그룹으로 나눠 멀티캐스트를 최대 2회 보낸다. 회원별로 쪼개지 않아 FCM 왕복은 1회에서 2회로만 늘고 O(1)이 유지된다. 무음 표현은 iOS는 `interruption-level: passive`, Android는 별도 저importance 알림 채널로 한다.

**Tech Stack:** Spring Boot 3.5.3 / Java 17 / MySQL / Flyway / firebase-admin 9.7.0 / JUnit5 + Mockito + AssertJ

**설계 문서:** `docs/superpowers/specs/2026-08-10-notification-quiet-hours-design.md`
**UI 목업:** https://claude.ai/code/artifact/7689e6d6-0c07-48fc-aae0-b9739097cb15

## 범위

**이 계획은 백엔드(`nar` 레포)만 다룬다.** 이유:

- iOS 무음은 서버 payload만으로 완결된다 — 이 계획만 배포해도 iOS 유저에게 동작한다.
- Android 무음은 앱이 새 알림 채널을 만들어야 하고, 이는 별도 레포(`warding-mobile-repo`) + 스토어 심사 사이클이다.
- 설정 UI도 같은 앱 레포다.

플러터 작업은 별도 계획으로 `warding-mobile-repo`에서 세운다. 백엔드를 먼저 머지해도
`quiet_hours_enabled` 기본값이 0이라 아무 동작도 바뀌지 않는다(사실상 feature flag OFF 상태로 머지).

## Global Constraints

- 작업 디렉토리는 워크트리 `/Users/changha/dev/nar-worktrees/feat-notification-quiet-hours` (브랜치 `feat/notification-quiet-hours`). **메인 레포 `/Users/changha/dev/nar`를 직접 수정하지 않는다.**
- 모든 주석·문서·커밋 메시지는 한국어.
- Android 무음 채널 id는 정확히 `warding_quiet` — 플러터가 같은 문자열로 채널을 만든다. 오타 나면 알림이 유실된다.
- `quiet_hours_enabled` 기본값은 **0(OFF)**. 구버전 앱에 없는 채널 id를 보내면 Android가 알림을 못 띄우므로, "켜져 있다 = 신버전 앱이다"가 성립해야 한다. 안전 요구사항이며 취향이 아니다.
- 시각 판정은 **Java에서** 한다. SQL로 시각을 비교하지 않는다 — 커밋 `914d932`에서 DB 세션 타임존 때문에 9시간 밀린 것과 같은 함정이다.
- 타임존은 `Asia/Seoul` 고정. 유저별 타임존 컬럼을 만들지 않는다.
- 잠자기 조회가 실패해도 푸시는 나가야 한다. 조회 실패 시 전원을 "소리 나는" 쪽으로 처리한다.
- 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>` 을 붙인다.
- 테스트 실행: `./gradlew test --tests "<FQCN>"`. 전체는 `./gradlew test`.

## File Structure

**신규**

| 파일 | 책임 |
|---|---|
| `src/main/resources/db/migration/V65__Add_member_quiet_hours.sql` | 컬럼 3개 추가 |
| `src/main/java/com/toy/nar/domain/member/repository/dto/MemberQuietHours.java` | projection DTO |
| `src/main/java/com/toy/nar/app/mobile/push/QuietHoursResolver.java` | 기존 `AppConfig.clock()` 빈을 주입받아 KST 로 고정, 회원 집합 → 지금 잠자기인 회원 집합 |
| `src/main/java/com/toy/nar/app/mobile/push/QuietAwarePushSender.java` | 2그룹 분할 발송 + 결과 병합 |
| `src/main/java/com/toy/nar/app/mobile/notification/dto/QuietHoursResponse.java` | GET 응답 |
| `src/main/java/com/toy/nar/app/mobile/notification/dto/QuietHoursUpdateRequest.java` | PUT 요청 |
| `src/main/java/com/toy/nar/app/mobile/notification/MobileQuietHoursService.java` | 조회·저장·검증 |
| `src/main/java/com/toy/nar/api/mobile/notification/MobileQuietHoursController.java` | `/api/mobile/me/quiet-hours` |
| `src/test/java/com/toy/nar/app/mobile/push/QuietHoursResolverTest.java` | wrap-around 판정 |
| `src/test/java/com/toy/nar/app/mobile/push/QuietAwarePushSenderTest.java` | 분할·병합·조회 실패 |
| `src/test/java/com/toy/nar/app/mobile/notification/MobileQuietHoursServiceTest.java` | 검증 400 |

**수정**

| 파일 | 변경 |
|---|---|
| `domain/member/entity/Member.java` | 필드 3개 + `updateQuietHours` |
| `domain/member/repository/MemberRepository.java` | projection 쿼리 |
| `app/mobile/push/MobilePushMessage.java` | `silent` 컴포넌트 + `asSilent()` |
| `app/mobile/push/MobilePushResult.java` | `merge` |
| `app/mobile/push/MobilePushGateway.java` | `sendToTopic` **삭제** |
| `app/mobile/push/FirebaseMobilePushGateway.java` | 무음 분기, 토픽 발송 삭제 |
| `app/mobile/push/PlayerSoloRankPushService.java` | 토픽 발송 삭제, 발송을 sender 경유로 |
| `app/mobile/push/TeamLiveEventPushService.java` | 발송을 sender 경유로 |
| `common/error/ErrorCode.java` | `INVALID_QUIET_HOURS` |
| `src/test/java/com/toy/nar/app/mobile/push/PlayerSoloRankPushServiceTest.java` | 토픽 테스트 2개 삭제, 생성자·발송 stub |
| `src/test/java/com/toy/nar/app/mobile/push/PlayerSoloRankPushFanOutBatchTest.java` | 생성자·발송 검증을 sender 로 |
| `src/test/java/com/toy/nar/app/mobile/push/TeamLiveEventPushFanOutBatchTest.java` | 생성자·발송 검증을 sender 로 |
| `src/test/java/com/toy/nar/app/mobile/push/TeamLiveEventPushServiceBestOfTest.java` | 생성자 인자 1개 추가 |
| `src/test/java/com/toy/nar/app/mobile/push/TeamLiveEventPushServiceScoreLineTest.java` | 생성자 인자 1개 추가 |

## O(1) 보증이 2층으로 갈린다

기존 팬아웃 테스트(`PlayerSoloRankPushFanOutBatchTest`, `TeamLiveEventPushFanOutBatchTest`)는
"구독자 수와 무관하게 발송 1회"를 지키는 회귀 가드다. 실측 근거가 주석에 있다 —
2026-07-29 구독자 1,548명 SET_START 이 1,074초(1명당 0.69초).

발송이 sender 경유로 바뀌면 이 가드가 두 층으로 갈린다. **둘 다 있어야 한다.**

| 층 | 지키는 것 | 어디서 |
|---|---|---|
| 서비스 | 회원별로 쪼개지 않는다 — sender 호출 **1회** | 기존 팬아웃 테스트 (`quietAwarePushSender` 로 검증 대상 교체) |
| sender | 그룹 수만큼만 쪼갠다 — 게이트웨이 호출 **최대 2회** | `QuietAwarePushSenderTest` (Task 4) |

서비스 테스트에서 게이트웨이 호출 횟수를 세는 것으로 대체하면 안 된다. sender 를 목으로 두면
게이트웨이는 아예 안 불린다.

## 생성자 인자는 맨 뒤에 붙인다

`@RequiredArgsConstructor` 는 필드 선언 순서로 생성자를 만든다. `quietAwarePushSender` 를
**필드 목록 맨 뒤**에 선언해 기존 인자 순서를 건드리지 않는다. 중간에 끼우면 테스트 5개의
인자 순서를 전부 다시 맞춰야 하고, 타입이 같은 인자가 섞이면 조용히 잘못 배선된다.

- `PlayerSoloRankPushService`: 4 → 5번째 인자
- `TeamLiveEventPushService`: 8 → 9번째 인자

## 설계 문서와 달라진 점

설계 문서는 "현재 설정은 기존 회원 정보 조회 응답에 필드로 얹는다"고 했지만, 이 계획은
`GET /api/mobile/me/quiet-hours`를 따로 둔다. 앱이 이미 파싱하는 회원 정보 DTO를 건드리지 않는 쪽이
회귀 위험이 작고, 파일 하나로 끝난다.

---

### Task 1: 스키마 + 엔티티 + projection 쿼리

**Files:**
- Create: `src/main/resources/db/migration/V65__Add_member_quiet_hours.sql`
- Create: `src/main/java/com/toy/nar/domain/member/repository/dto/MemberQuietHours.java`
- Modify: `src/main/java/com/toy/nar/domain/member/entity/Member.java`
- Modify: `src/main/java/com/toy/nar/domain/member/repository/MemberRepository.java`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces:
  - `Member#isQuietHoursEnabled(): boolean`, `Member#getQuietStartTime(): LocalTime`, `Member#getQuietEndTime(): LocalTime`
  - `Member#updateQuietHours(boolean enabled, LocalTime startTime, LocalTime endTime): void`
  - `record MemberQuietHours(Long memberId, boolean enabled, LocalTime startTime, LocalTime endTime)`
  - `MemberRepository#findQuietHoursByMemberIds(Collection<Long> memberIds): List<MemberQuietHours>` — **`enabled = true`인 회원만 돌려준다.** 꺼둔 회원은 결과에 없다.

- [ ] **Step 1: 마이그레이션 작성**

`src/main/resources/db/migration/V65__Add_member_quiet_hours.sql`:

```sql
-- 알림 잠자기(방해금지 시간). 이 시간대에는 푸시를 소리 없이 보내 알림함에만 쌓는다.
-- 기본값 OFF: Android 무음은 신버전 앱이 만드는 채널이 있어야 하고 서버는 앱 버전을 모른다.
-- 켜져 있다 = 신버전 앱이다 가 성립해야 구버전 기기에서 알림이 유실되지 않는다.
ALTER TABLE member
  ADD COLUMN quiet_hours_enabled TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN quiet_start_time TIME NOT NULL DEFAULT '01:00:00',
  ADD COLUMN quiet_end_time   TIME NOT NULL DEFAULT '08:00:00';
```

- [ ] **Step 2: 마이그레이션 번호 충돌 확인**

Run: `ls src/main/resources/db/migration/ | sed 's/__.*//' | sort -t V -k2 -n | tail -3`
Expected: `V63`, `V64`, `V65` — `V65`가 유일하게 새로 생긴 번호여야 한다. 다른 브랜치가 이미 `V65`를 썼다면 다음 빈 번호로 올린다.

- [ ] **Step 3: projection DTO 작성**

`src/main/java/com/toy/nar/domain/member/repository/dto/MemberQuietHours.java`:

```java
package com.toy.nar.domain.member.repository.dto;

import java.time.LocalTime;

/**
 * 잠자기 판정에 필요한 최소 필드만 담는 projection.
 *
 * <p>기기 목록에서 {@code device.getMember()} 로 엔티티를 타면 구독자 수만큼 프록시가
 * 초기화된다(1,500명이면 쿼리 1,500방). 회원 id 집합으로 이 DTO만 한 방에 받아 N+1을 피한다.</p>
 */
public record MemberQuietHours(
		Long memberId,
		boolean enabled,
		LocalTime startTime,
		LocalTime endTime) {
}
```

- [ ] **Step 4: Member 엔티티에 필드·메서드 추가**

`Member.java`의 `createdAt` 필드 선언 바로 뒤에 추가:

```java
	@Column(name = "quiet_hours_enabled", nullable = false)
	private boolean quietHoursEnabled;

	@Column(name = "quiet_start_time", nullable = false)
	private LocalTime quietStartTime = LocalTime.of(1, 0);

	@Column(name = "quiet_end_time", nullable = false)
	private LocalTime quietEndTime = LocalTime.of(8, 0);
```

클래스 끝(마지막 메서드 뒤)에 추가:

```java
	/** 알림 잠자기 설정을 갱신한다. 값 검증은 서비스에서 끝낸 뒤 호출한다. */
	public void updateQuietHours(boolean enabled, LocalTime startTime, LocalTime endTime) {
		this.quietHoursEnabled = enabled;
		this.quietStartTime = startTime;
		this.quietEndTime = endTime;
	}
```

import 추가: `java.time.LocalTime`.

- [ ] **Step 5: 리포지토리 쿼리 추가**

`MemberRepository.java`에 추가:

```java
	/**
	 * 잠자기를 켜둔 회원의 설정만 한 방에 조회한다. 꺼둔 회원은 결과에 없다.
	 * 시각 비교는 Java 에서 한다 — SQL 로 비교하면 DB 세션 타임존에 따라 밀린다(914d932).
	 */
	@Query("""
			select new com.toy.nar.domain.member.repository.dto.MemberQuietHours(
				m.id, m.quietHoursEnabled, m.quietStartTime, m.quietEndTime)
			from Member m
			where m.id in :memberIds
			  and m.quietHoursEnabled = true
			""")
	List<MemberQuietHours> findQuietHoursByMemberIds(@Param("memberIds") Collection<Long> memberIds);
```

필요한 import를 확인해 없으면 추가: `com.toy.nar.domain.member.repository.dto.MemberQuietHours`, `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`, `java.util.Collection`, `java.util.List`.

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: JPQL 검증 (컨텍스트 로드)**

JPQL의 `select new` FQCN 오타는 컴파일에서 안 잡히고 컨텍스트 로드 때 잡힌다. 로컬 MySQL이 필요하다.

Run:
```bash
docker-compose up -d
./gradlew test --tests "com.toy.nar.NarApplicationTests"
```
Expected: PASS. `QuerySyntaxException` 이나 `Unable to locate class` 가 나오면 FQCN·필드명을 고친다.

로컬 MySQL을 띄울 수 없는 환경이면 이 단계를 건너뛰고 Task 7 이후 `./gradlew test` 로 한 번에 확인한다. 건너뛰었다면 커밋 메시지에 적지 않고, 사용자에게 "JPQL 컨텍스트 검증 미실행"을 보고한다.

- [ ] **Step 8: 커밋**

```bash
git add src/main/resources/db/migration/V65__Add_member_quiet_hours.sql \
        src/main/java/com/toy/nar/domain/member/repository/dto/MemberQuietHours.java \
        src/main/java/com/toy/nar/domain/member/entity/Member.java \
        src/main/java/com/toy/nar/domain/member/repository/MemberRepository.java
git commit -m "$(cat <<'EOF'
feat: member 에 알림 잠자기 시간 컬럼 추가

기본값 OFF. Android 무음은 신버전 앱이 만드는 채널이 필요하고 서버는 앱 버전을
모르므로, "켜져 있다 = 신버전" 이 성립해야 구버전 기기 알림 유실을 막는다.

잠자기 설정 조회는 projection 1방으로 한다. 기기 목록에서 member 프록시를 타면
구독자 수만큼 초기화돼 N+1 이 된다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: 잠자기 판정 (`QuietHoursResolver`)

**Files:**
- Create: `src/main/java/com/toy/nar/app/mobile/push/QuietHoursResolver.java`
- Test: `src/test/java/com/toy/nar/app/mobile/push/QuietHoursResolverTest.java`

**Interfaces:**
- Consumes: `MemberRepository#findQuietHoursByMemberIds`, `MemberQuietHours` (Task 1), 기존 `AppConfig#clock()` 빈
- Produces:
  - `QuietHoursResolver#quietMemberIds(Collection<Long> memberIds): Set<Long>` — 지금 잠자기 구간에 있는 회원 id. 빈 입력이면 `Set.of()`.
  - `QuietHoursResolver#isWithin(LocalTime now, LocalTime start, LocalTime end): boolean` (package-private static, 테스트용)

새 `Clock` 빈은 만들지 않는다. `AppConfig`가 이미 `clock()` 빈을 등록하고 있어(`Clock.systemDefaultZone()`),
같은 이름으로 `ClockConfig`를 또 만들면 빈 이름 충돌로 `BeanDefinitionOverrideException`이 터져 부팅이
죽는다. 대신 기존 빈을 그대로 주입받고, `QuietHoursResolver` 안에서 판정 시점에
`clock.withZone(ZoneId.of("Asia/Seoul"))`로 KST를 고정한다 — 주입된 시계의 존이 무엇이든 여기서 덮어쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/toy/nar/app/mobile/push/QuietHoursResolverTest.java`:

```java
package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.dto.MemberQuietHours;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuietHoursResolverTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Mock
	private MemberRepository memberRepository;

	/** KST 로 주어진 시각에 고정된 시계. */
	private static Clock fixedAt(int hour, int minute) {
		return Clock.fixed(
				LocalTime.of(hour, minute).atDate(java.time.LocalDate.of(2026, 8, 10)).atZone(KST).toInstant(),
				KST);
	}

	@Test
	void 같은_날_구간은_시작은_포함하고_종료는_제외한다() {
		LocalTime start = LocalTime.of(1, 0);
		LocalTime end = LocalTime.of(8, 0);

		assertThat(QuietHoursResolver.isWithin(LocalTime.of(0, 59), start, end)).isFalse();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(1, 0), start, end)).isTrue();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(7, 59), start, end)).isTrue();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(8, 0), start, end)).isFalse();
	}

	@Test
	void 자정을_넘는_구간도_판정한다() {
		LocalTime start = LocalTime.of(23, 0);
		LocalTime end = LocalTime.of(8, 0);

		assertThat(QuietHoursResolver.isWithin(LocalTime.of(23, 30), start, end)).isTrue();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(0, 30), start, end)).isTrue();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(12, 0), start, end)).isFalse();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(22, 59), start, end)).isFalse();
	}

	@Test
	void 구간에_든_회원만_돌려준다() {
		when(memberRepository.findQuietHoursByMemberIds(Set.of(1L, 2L))).thenReturn(List.of(
				new MemberQuietHours(1L, true, LocalTime.of(1, 0), LocalTime.of(8, 0)),
				new MemberQuietHours(2L, true, LocalTime.of(13, 0), LocalTime.of(14, 0))));
		QuietHoursResolver resolver = new QuietHoursResolver(memberRepository, fixedAt(2, 30));

		assertThat(resolver.quietMemberIds(Set.of(1L, 2L))).containsExactly(1L);
	}

	@Test
	void 빈_입력이면_쿼리하지_않는다() {
		QuietHoursResolver resolver = new QuietHoursResolver(memberRepository, fixedAt(2, 30));

		assertThat(resolver.quietMemberIds(Set.of())).isEmpty();
		verifyNoInteractions(memberRepository);
	}

	@Test
	void 조회가_실패하면_아무도_잠자기로_보지_않는다() {
		when(memberRepository.findQuietHoursByMemberIds(any()))
				.thenThrow(new RuntimeException("db down"));
		QuietHoursResolver resolver = new QuietHoursResolver(memberRepository, fixedAt(2, 30));

		assertThat(resolver.quietMemberIds(Set.of(1L))).isEmpty();
	}
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "com.toy.nar.app.mobile.push.QuietHoursResolverTest"`
Expected: 컴파일 실패 — `cannot find symbol: class QuietHoursResolver`

- [ ] **Step 3: 기존 Clock 빈 확인**

`src/main/java/com/toy/nar/config/AppConfig.java`에 이미 `@Bean public Clock clock()`이 있다.
새 `ClockConfig`를 만들지 않는다 — 빈 이름이 겹쳐 `BeanDefinitionOverrideException`으로 부팅이 죽는다.
KST 고정은 이 빈을 주입받는 `QuietHoursResolver` 쪽 책임으로 넘긴다(Step 4).

- [ ] **Step 4: 판정 컴포넌트 작성**

`src/main/java/com/toy/nar/app/mobile/push/QuietHoursResolver.java`:

```java
package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.dto.MemberQuietHours;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** 지금 알림 잠자기 구간에 있는 회원을 가려낸다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuietHoursResolver {

	/** 잠자기 판정은 KST 기준이다. 주입된 시계의 존이 무엇이든 여기서 고정한다. */
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final MemberRepository memberRepository;
	private final Clock clock;

	/**
	 * [memberIds] 중 지금 잠자기 구간에 있는 회원 id.
	 *
	 * <p>조회가 실패하면 빈 집합을 준다 — 조용해지는 것보다 알림이 나가는 게 낫고,
	 * 잠자기 조회 장애가 푸시 전체를 막아선 안 된다.</p>
	 */
	public Set<Long> quietMemberIds(Collection<Long> memberIds) {
		if (memberIds.isEmpty()) {
			return Set.of();
		}
		LocalTime now = LocalTime.now(clock.withZone(KOREA));
		try {
			return memberRepository.findQuietHoursByMemberIds(memberIds).stream()
					.filter(quiet -> isWithin(now, quiet.startTime(), quiet.endTime()))
					.map(MemberQuietHours::memberId)
					.collect(Collectors.toSet());
		} catch (Exception e) {
			log.warn("Failed to resolve quiet hours members={} — 전원 소리 있는 발송으로 처리", memberIds.size(), e);
			return Set.of();
		}
	}

	/** 시작 포함, 종료 제외. start > end 면 자정을 넘는 구간이다. */
	static boolean isWithin(LocalTime now, LocalTime start, LocalTime end) {
		return start.isBefore(end)
				? !now.isBefore(start) && now.isBefore(end)
				: !now.isBefore(start) || now.isBefore(end);
	}
}
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew test --tests "com.toy.nar.app.mobile.push.QuietHoursResolverTest"`
Expected: 5개 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/toy/nar/app/mobile/push/QuietHoursResolver.java \
        src/test/java/com/toy/nar/app/mobile/push/QuietHoursResolverTest.java
git commit -m "$(cat <<'EOF'
feat: 알림 잠자기 시각 판정 QuietHoursResolver

시각 비교는 Java LocalTime + Asia/Seoul 로 한다. SQL 로 비교하면 DB 세션
타임존에 따라 밀린다(914d932).

자정을 넘는 구간(23:00~08:00)이 유일한 비자명 분기라 경계값으로 덮었다.
조회 실패 시 빈 집합을 줘서 잠자기 장애가 푸시를 막지 않게 한다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: 게이트웨이 무음 + 죽은 토픽 발송 삭제

**Files:**
- Modify: `src/main/java/com/toy/nar/app/mobile/push/MobilePushMessage.java`
- Modify: `src/main/java/com/toy/nar/app/mobile/push/MobilePushGateway.java`
- Modify: `src/main/java/com/toy/nar/app/mobile/push/FirebaseMobilePushGateway.java`
- Modify: `src/main/java/com/toy/nar/app/mobile/push/PlayerSoloRankPushService.java` (토픽 발송 제거만)
- Modify: `src/test/java/com/toy/nar/app/mobile/push/PlayerSoloRankPushServiceTest.java` (토픽 테스트 삭제)

**Interfaces:**
- Consumes: 없음
- Produces:
  - `record MobilePushMessage(String title, String body, Map<String, String> data, boolean silent)` — 기존 3-인자 생성자가 `silent=false`로 위임하므로 기존 호출처는 무수정
  - `MobilePushMessage#asSilent(): MobilePushMessage`
  - `MobilePushGateway`에서 `sendToTopic` **제거** — 남는 메서드는 `isAvailable()`, `send(List<String>, MobilePushMessage)`

**배경:** `all_solo_rank` 토픽 발송은 플러터 앱에 `subscribeToTopic` 호출이 하나도 없어 아무도 받지 않는다. 지우면 잠자기의 최대 걸림돌(토픽은 회원별 판정 불가)이 사라지고, 게이트웨이의 중복 빌더 2개가 1개로 줄어 무음 분기를 한 곳만 고치면 된다.

- [ ] **Step 1: `MobilePushMessage`에 silent 추가**

```java
package com.toy.nar.app.mobile.push;

import java.util.Map;

/**
 * 푸시 한 건.
 *
 * @param silent 알림 잠자기 시간대 발송. 소리·배너 없이 알림함에만 쌓이게 보낸다.
 */
public record MobilePushMessage(
		String title,
		String body,
		Map<String, String> data,
		boolean silent) {

	/** 기존 호출처를 위한 소리 있는 발송. */
	public MobilePushMessage(String title, String body, Map<String, String> data) {
		this(title, body, data, false);
	}

	public MobilePushMessage asSilent() {
		return new MobilePushMessage(title, body, data, true);
	}
}
```

- [ ] **Step 2: 인터페이스에서 `sendToTopic` 삭제**

`MobilePushGateway.java`에서 `sendToTopic` 선언과 그 javadoc을 지운다. 결과:

```java
package com.toy.nar.app.mobile.push;

import java.util.List;

public interface MobilePushGateway {

	boolean isAvailable();

	MobilePushResult send(List<String> tokens, MobilePushMessage message);
}
```

- [ ] **Step 3: 게이트웨이 무음 분기 + 토픽 구현 삭제**

`FirebaseMobilePushGateway.java`에서 `sendToTopic` 메서드 전체(`:59-86`)를 지우고, `sendBatch`를 아래로 교체한다. 쓰지 않게 된 import(`Message`, `FirebaseMessagingException`은 `sendBatch`에서 계속 쓰므로 유지, `Message`만 제거)를 정리한다.

클래스 상단 상수에 추가:

```java
	/** Android 무음 발송용 저importance 채널. 플러터가 같은 id 로 채널을 만든다. */
	private static final String QUIET_CHANNEL_ID = "warding_quiet";
```

`sendBatch` 교체:

```java
	private BatchResponse sendBatch(
			FirebaseMessaging firebaseMessaging,
			List<String> tokens,
			MobilePushMessage message) {
		MulticastMessage multicastMessage = MulticastMessage.builder()
				.setNotification(Notification.builder()
						.setTitle(message.title())
						.setBody(message.body())
						.build())
				.putAllData(message.data())
				.setAndroidConfig(androidConfig(message))
				.setApnsConfig(apnsConfig(message))
				.addAllTokens(tokens)
				.build();
		try {
			return firebaseMessaging.sendEachForMulticast(multicastMessage);
		} catch (FirebaseMessagingException e) {
			throw new IllegalStateException("FCM 멀티캐스트 발송에 실패했습니다.", e);
		}
	}

	/**
	 * Android O+ 는 채널 설정이 payload 보다 우선한다 — priority 만 낮춰선 소리가 그대로 난다.
	 * 그래서 무음은 저importance 채널을 따로 지정해야 한다. 채널은 앱이 만든다.
	 */
	private AndroidConfig androidConfig(MobilePushMessage message) {
		AndroidConfig.Builder builder = AndroidConfig.builder()
				.setPriority(message.silent() ? AndroidConfig.Priority.NORMAL : AndroidConfig.Priority.HIGH);
		if (message.silent()) {
			builder.setNotification(AndroidNotification.builder()
					.setChannelId(QUIET_CHANNEL_ID)
					.build());
		}
		return builder.build();
	}

	/** iOS 무음은 서버만으로 된다 — sound 를 비우고 passive 로 보내면 배너 없이 알림함에만 남는다. */
	private ApnsConfig apnsConfig(MobilePushMessage message) {
		Aps.Builder aps = Aps.builder();
		if (message.silent()) {
			aps.putCustomData("interruption-level", "passive");
		} else {
			aps.setSound("default");
		}
		return ApnsConfig.builder().setAps(aps.build()).build();
	}
```

import 추가: `com.google.firebase.messaging.AndroidNotification`.

- [ ] **Step 4: 솔랭 서비스에서 토픽 발송 제거**

`PlayerSoloRankPushService.java`에서 지운다:
- `ALL_SOLO_RANK_TOPIC` 상수(`:30-31`, javadoc 포함)
- `sendToAllSoloRankTopic` 메서드 전체(`:98-108`)
- `dispatch`의 호출부 — `:80-81`의 주석 2줄과 호출 1줄

`dispatch`는 이렇게 남는다:

```java
	private void dispatch(Player player, String gameId, MobilePushMessage message) {
		try {
			fanOutBatched(
					deviceRepository.findActiveDevicesBySubscribedPlayerId(player.getId()),
					player,
					gameId,
					message);
		} catch (Exception e) {
			log.warn(
					"Failed to prepare player solo rank pushes playerId={} gameId={}",
					player.getId(),
					gameId,
					e);
		}
	}
```

- [ ] **Step 5: 토픽 테스트 2개 삭제**

`PlayerSoloRankPushServiceTest.java`에서 `sendsToAllSoloRankTopicOncePerGame()` 과 `topicSendFailureDoesNotBlockSubscriberPush()` 두 테스트 메서드를 지운다. 쓰지 않게 된 import(`times`, `doThrow`, `assertThatCode`)가 남으면 정리한다 — 남은 테스트에서 쓰이는지 확인 후 판단한다.

- [ ] **Step 6: 컴파일 + 기존 테스트 확인**

Run: `./gradlew compileJava compileTestJava && ./gradlew test --tests "com.toy.nar.app.mobile.push.*"`
Expected: BUILD SUCCESSFUL, 남은 테스트 전부 PASS. `sendToTopic` 참조가 남아 컴파일이 깨지면 그 호출처도 지운다.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/toy/nar/app/mobile/push/ src/test/java/com/toy/nar/app/mobile/push/PlayerSoloRankPushServiceTest.java
git commit -m "$(cat <<'EOF'
feat: 무음 푸시 발송 지원 + 죽은 all_solo_rank 토픽 발송 삭제

토픽 발송은 플러터에 subscribeToTopic 호출이 없어 아무도 받지 않았다. 지우면
잠자기의 최대 걸림돌(토픽은 회원별 판정 불가)이 사라지고 게이트웨이 중복
빌더도 1개로 줄어든다.

무음 표현:
- iOS: sound 생략 + interruption-level passive (서버만으로 완결)
- Android: warding_quiet 채널 지정. Android O+ 는 채널 설정이 payload 보다
  우선해서 priority 만 낮추면 소리가 그대로 난다. 채널은 앱이 만든다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: 2그룹 분할 발송기 (`QuietAwarePushSender`)

**Files:**
- Create: `src/main/java/com/toy/nar/app/mobile/push/QuietAwarePushSender.java`
- Modify: `src/main/java/com/toy/nar/app/mobile/push/MobilePushResult.java`
- Test: `src/test/java/com/toy/nar/app/mobile/push/QuietAwarePushSenderTest.java`

**Interfaces:**
- Consumes: `QuietHoursResolver#quietMemberIds` (Task 2), `MobilePushMessage#asSilent` (Task 3), `MobilePushGateway#send`
- Produces:
  - `QuietAwarePushSender#send(Map<Long, List<String>> tokensByMember, MobilePushMessage message): MobilePushResult`
  - `MobilePushResult#merge(MobilePushResult other): MobilePushResult`

**왜 공용 컴포넌트인가:** 솔랭(`PlayerSoloRankPushService.fanOutBatched`)과 경기(`TeamLiveEventPushService.fanOutBatched`) 두 곳이 똑같이 필요하다. 각자에 분할·병합을 복붙하면 두 벌이 된다.

**왜 회원별로 쪼개지 않는가:** 두 서비스가 배치 멀티캐스트를 쓰는 건 실측 근거가 있다 — 2026-08-04 프로덕션에서 Oner 구독자 1,502명 개별 발송이 472초 걸려 솔랭 폴 스레드를 통째로 막았다(신규 게임 감지 0건, 알림 10분 지연). 2그룹이면 왕복이 1→2회로만 늘어 O(1)이 유지된다.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/toy/nar/app/mobile/push/QuietAwarePushSenderTest.java`:

```java
package com.toy.nar.app.mobile.push;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuietAwarePushSenderTest {

	@Mock
	private MobilePushGateway pushGateway;

	@Mock
	private QuietHoursResolver quietHoursResolver;

	private final MobilePushMessage message =
			new MobilePushMessage("제목", "본문", Map.of("type", "TEST"));

	private QuietAwarePushSender sender() {
		return new QuietAwarePushSender(pushGateway, quietHoursResolver);
	}

	private static Map<Long, List<String>> tokens() {
		Map<Long, List<String>> byMember = new LinkedHashMap<>();
		byMember.put(1L, List.of("loud-a", "loud-b"));
		byMember.put(2L, List.of("quiet-a"));
		return byMember;
	}

	@Test
	void 잠자기_회원과_아닌_회원을_따로_보낸다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of(2L));
		when(pushGateway.send(eq(List.of("loud-a", "loud-b")), any()))
				.thenReturn(new MobilePushResult(2, 0, List.of(), List.of("loud-a", "loud-b")));
		when(pushGateway.send(eq(List.of("quiet-a")), any()))
				.thenReturn(new MobilePushResult(1, 0, List.of(), List.of("quiet-a")));

		MobilePushResult result = sender().send(tokens(), message);

		verify(pushGateway).send(List.of("loud-a", "loud-b"), message);
		verify(pushGateway).send(List.of("quiet-a"), message.asSilent());
		assertThat(result.successCount()).isEqualTo(3);
		assertThat(result.successTokens()).containsExactlyInAnyOrder("loud-a", "loud-b", "quiet-a");
	}

	@Test
	void 잠자기_회원이_없으면_한_번만_보낸다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of());
		when(pushGateway.send(any(), any()))
				.thenReturn(new MobilePushResult(3, 0, List.of(), List.of("loud-a", "loud-b", "quiet-a")));

		sender().send(tokens(), message);

		verify(pushGateway, times(1)).send(any(), any());
		verify(pushGateway).send(List.of("loud-a", "loud-b", "quiet-a"), message);
	}

	@Test
	void 전원_잠자기면_무음으로_한_번만_보낸다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of(1L, 2L));
		when(pushGateway.send(any(), any()))
				.thenReturn(new MobilePushResult(3, 0, List.of(), List.of()));

		sender().send(tokens(), message);

		verify(pushGateway, times(1)).send(any(), any());
		verify(pushGateway).send(List.of("loud-a", "loud-b", "quiet-a"), message.asSilent());
	}

	@Test
	void 실패건수와_무효토큰도_합친다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of(2L));
		when(pushGateway.send(eq(List.of("loud-a", "loud-b")), any()))
				.thenReturn(new MobilePushResult(1, 1, List.of("loud-b"), List.of("loud-a")));
		when(pushGateway.send(eq(List.of("quiet-a")), any()))
				.thenReturn(new MobilePushResult(0, 1, List.of("quiet-a"), List.of()));

		MobilePushResult result = sender().send(tokens(), message);

		assertThat(result.failureCount()).isEqualTo(2);
		assertThat(result.invalidTokens()).containsExactlyInAnyOrder("loud-b", "quiet-a");
	}
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "com.toy.nar.app.mobile.push.QuietAwarePushSenderTest"`
Expected: 컴파일 실패 — `cannot find symbol: class QuietAwarePushSender`

- [ ] **Step 3: `MobilePushResult.merge` 추가**

`MobilePushResult.java`의 record 본문에 추가:

```java
	/** 잠자기 분할 발송처럼 여러 번 보낸 결과를 하나로 합친다. */
	public MobilePushResult merge(MobilePushResult other) {
		return new MobilePushResult(
				successCount + other.successCount,
				failureCount + other.failureCount,
				concat(invalidTokens, other.invalidTokens),
				concat(successTokens, other.successTokens));
	}

	private static List<String> concat(List<String> left, List<String> right) {
		return Stream.concat(left.stream(), right.stream()).toList();
	}
```

import 추가: `java.util.stream.Stream`.

컴포넌트 이름이 다르면(`successCount` 등) 실제 record 선언에 맞춘다.

- [ ] **Step 4: 발송기 작성**

`src/main/java/com/toy/nar/app/mobile/push/QuietAwarePushSender.java`:

```java
package com.toy.nar.app.mobile.push;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 알림 잠자기를 반영해 멀티캐스트한다.
 *
 * <p>구독자를 잠자기 걸린 집합 / 안 걸린 집합 2그룹으로 나눠 최대 2회 보낸다.
 * 회원별로 쪼개지 않는다 — 2026-08-04 프로덕션에서 구독자 1,502명 개별 발송이 472초 걸려
 * 솔랭 폴 스레드를 통째로 막은 적이 있다. 2그룹이면 FCM 왕복이 1→2회로만 늘고 O(1)이 유지된다.</p>
 */
@Component
@RequiredArgsConstructor
public class QuietAwarePushSender {

	private final MobilePushGateway pushGateway;
	private final QuietHoursResolver quietHoursResolver;

	public MobilePushResult send(Map<Long, List<String>> tokensByMember, MobilePushMessage message) {
		Set<Long> quietMemberIds = quietHoursResolver.quietMemberIds(tokensByMember.keySet());

		List<String> loudTokens = new ArrayList<>();
		List<String> quietTokens = new ArrayList<>();
		tokensByMember.forEach((memberId, tokens) ->
				(quietMemberIds.contains(memberId) ? quietTokens : loudTokens).addAll(tokens));

		if (quietTokens.isEmpty()) {
			return pushGateway.send(loudTokens, message);
		}
		if (loudTokens.isEmpty()) {
			return pushGateway.send(quietTokens, message.asSilent());
		}
		return pushGateway.send(loudTokens, message)
				.merge(pushGateway.send(quietTokens, message.asSilent()));
	}
}
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew test --tests "com.toy.nar.app.mobile.push.QuietAwarePushSenderTest"`
Expected: 4개 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/toy/nar/app/mobile/push/QuietAwarePushSender.java \
        src/main/java/com/toy/nar/app/mobile/push/MobilePushResult.java \
        src/test/java/com/toy/nar/app/mobile/push/QuietAwarePushSenderTest.java
git commit -m "$(cat <<'EOF'
feat: 잠자기 반영 분할 발송기 QuietAwarePushSender

구독자를 잠자기 걸린 집합 / 안 걸린 집합 2그룹으로 나눠 최대 2회 멀티캐스트한다.
회원별로 쪼개지 않는다 — 2026-08-04 프로덕션에서 구독자 1,502명 개별 발송이
472초 걸려 솔랭 폴 스레드를 막은 적이 있다. 2그룹이면 왕복이 1→2회로만 늘고
O(1)이 유지된다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: 솔랭 발송을 sender 경유로

**Files:**
- Modify: `src/main/java/com/toy/nar/app/mobile/push/PlayerSoloRankPushService.java:156-159`
- Test: `src/test/java/com/toy/nar/app/mobile/push/PlayerSoloRankPushServiceTest.java`
- Test: `src/test/java/com/toy/nar/app/mobile/push/PlayerSoloRankPushFanOutBatchTest.java`

**Interfaces:**
- Consumes: `QuietAwarePushSender#send(Map<Long, List<String>>, MobilePushMessage)` (Task 4)
- Produces: 없음 (배선)

- [ ] **Step 1: 기존 테스트를 새 생성자로 고치고 실패 확인**

`PlayerSoloRankPushServiceTest.java`에 목을 추가하고 `setUp`을 고친다:

```java
	@Mock
	private QuietAwarePushSender quietAwarePushSender;
```

```java
	@BeforeEach
	void setUp() {
		service = new PlayerSoloRankPushService(
				deviceRepository, deliveryRepository, pushGateway, notificationService, quietAwarePushSender);
		when(pushGateway.isAvailable()).thenReturn(true);
	}
```

기존 `sendsOncePerSubscribedMemberAndDeactivatesInvalidTokens` 의 stub 을 sender 로 옮긴다:

```java
		when(quietAwarePushSender.send(any(), any()))
				.thenReturn(new MobilePushResult(1, 1, List.of("token-2"), List.of("token-1")));
```

(원래 있던 `when(pushGateway.send(any(), any()))` 줄은 지운다.)

그리고 잠자기 분할이 sender 로 위임되는지 확인하는 테스트를 추가한다:

```java
	@Test
	void 발송은_회원별_토큰맵으로_sender_에_위임한다() {
		Player player = player(10L, "Faker");
		MemberDevice first = device(1L, member(7L), "token-1");
		MemberDevice second = device(2L, member(8L), "token-2");

		when(deviceRepository.findActiveDevicesBySubscribedPlayerId(10L))
				.thenReturn(List.of(first, second));
		when(deliveryRepository.reserveAll(any(), eq(10L), eq("game-1"))).thenReturn(List.of(7L, 8L));
		when(quietAwarePushSender.send(any(), any()))
				.thenReturn(new MobilePushResult(2, 0, List.of(), List.of("token-1", "token-2")));

		service.notifySubscribers(player, "game-1", "아리", "ahri.png", "솔로 랭크", "https://www.op.gg/summoners/kr/Faker-KR1");

		verify(quietAwarePushSender).send(
				Map.of(7L, List.of("token-1"), 8L, List.of("token-2")),
				new MobilePushMessage(
						"Faker 선수가 솔랭을 시작했어요",
						"아리로 솔로 랭크 플레이 중",
						Map.of(
								"type", "PLAYER_SOLO_RANK_STARTED",
								"playerId", "10",
								"playerName", "Faker",
								"gameId", "game-1",
								"championName", "아리",
								"queueType", "솔로 랭크",
								"deepLink", "nar://players/10",
								"championImageUrl", "ahri.png",
								"opggUrl", "https://www.op.gg/summoners/kr/Faker-KR1")));
	}
```

import 추가: `java.util.Map`.

Run: `./gradlew test --tests "com.toy.nar.app.mobile.push.PlayerSoloRankPushServiceTest"`
Expected: 컴파일 실패 — 생성자 인자 개수 불일치

- [ ] **Step 2: 팬아웃 회귀 테스트도 sender 기준으로 고친다**

`PlayerSoloRankPushFanOutBatchTest.java` — 이 파일은 "구독자 수와 무관하게 발송 1회"를 지키는
회귀 가드다(주석의 실측: 1,502명 472초). 검증 대상을 게이트웨이에서 sender 로 옮긴다.

목 선언 추가:

```java
	private final QuietAwarePushSender quietAwarePushSender = mock(QuietAwarePushSender.class);
```

`setUp` 의 생성자 호출을 고친다:

```java
		service = new PlayerSoloRankPushService(
				deviceRepository, deliveryRepository, pushGateway, notificationService, quietAwarePushSender);
```

파일 안의 모든 `when(pushGateway.send(any(), any()))` 를 `when(quietAwarePushSender.send(any(), any()))`
로, 모든 `verify(pushGateway, ...)...send(...)` 를 `verify(quietAwarePushSender, ...)...send(...)` 로 바꾼다.

토큰 리스트를 캡처해 검증하던 곳은 인자 타입이 `Map<Long, List<String>>` 으로 바뀌므로 아래처럼 고친다:

```java
		ArgumentCaptor<Map<Long, List<String>>> byMember = ArgumentCaptor.forClass(Map.class);
		verify(quietAwarePushSender, times(1)).send(byMember.capture(), any());
		assertThat(byMember.getValue().values().stream().flatMap(List::stream).toList())
				.containsExactly("token-1", "token-2", "token-3");
```

import 추가: `java.util.Map`, `com.toy.nar.app.mobile.push.QuietAwarePushSender` (동일 패키지면 불필요).

`verify(pushGateway, never()).send(...)` 를 한 줄 추가해 게이트웨이 직접 호출이 되살아나지 않게 못 박는다.

- [ ] **Step 3: 서비스 배선**

`PlayerSoloRankPushService.java` 필드 선언 **맨 뒤**(`notificationService` 다음 줄)에 추가한다.
`@RequiredArgsConstructor` 가 선언 순서로 생성자를 만들므로, 중간에 끼우면 기존 인자 순서가 밀린다.

```java
	private final QuietAwarePushSender quietAwarePushSender;
```

`fanOutBatched` 의 발송부(`:156-159`)를 교체한다. 교체 전:

```java
		List<String> allTokens = tokensByMember.values().stream().flatMap(List::stream).toList();
		MobilePushResult result;
		try {
			result = pushGateway.send(allTokens, message);
```

교체 후:

```java
		List<String> allTokens = tokensByMember.values().stream().flatMap(List::stream).toList();
		MobilePushResult result;
		try {
			// 잠자기 회원은 무음으로 갈린다. 회원별로 쪼개지 않고 2그룹 멀티캐스트.
			result = quietAwarePushSender.send(tokensByMember, message);
```

`allTokens` 는 아래 로그(`tokens={}`)에서 계속 쓰므로 남긴다. `pushGateway` 는 `isAvailable()` 에서 계속 쓰므로 필드를 지우지 않는다.

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "com.toy.nar.app.mobile.push.PlayerSoloRank*"`
Expected: 두 파일 전부 PASS. `verify` 의 기대 메시지가 어긋나면 실제 `buildMessage` 출력(`KoreanParticle.ro` 결과 포함)에 맞춰 문자열을 고친다.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/toy/nar/app/mobile/push/PlayerSoloRankPushService.java \
        src/test/java/com/toy/nar/app/mobile/push/PlayerSoloRankPushServiceTest.java \
        src/test/java/com/toy/nar/app/mobile/push/PlayerSoloRankPushFanOutBatchTest.java
git commit -m "$(cat <<'EOF'
feat: 솔랭 푸시에 알림 잠자기 적용

발송을 QuietAwarePushSender 경유로 바꿔 잠자기 회원은 무음으로 갈린다.
배치 멀티캐스트는 유지 — 회원별 발송으로 되돌리면 472초 팬아웃 회귀다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: 경기 발송을 sender 경유로

**Files:**
- Modify: `src/main/java/com/toy/nar/app/mobile/push/TeamLiveEventPushService.java:384-387`
- Test: `src/test/java/com/toy/nar/app/mobile/push/TeamLiveEventPushFanOutBatchTest.java`
- Test: `src/test/java/com/toy/nar/app/mobile/push/TeamLiveEventPushServiceBestOfTest.java` (생성자만)
- Test: `src/test/java/com/toy/nar/app/mobile/push/TeamLiveEventPushServiceScoreLineTest.java` (생성자만)

**Interfaces:**
- Consumes: `QuietAwarePushSender#send(Map<Long, List<String>>, MobilePushMessage)` (Task 4)
- Produces: 없음 (배선)

- [ ] **Step 1: 팬아웃 회귀 테스트를 sender 기준으로 고치고 위임 검증을 추가한다**

`TeamLiveEventPushFanOutBatchTest.java` 에 목을 추가한다:

```java
	private final QuietAwarePushSender quietAwarePushSender = mock(QuietAwarePushSender.class);
```

`setUp` 의 생성자 호출을 고친다 — `quietAwarePushSender` 가 **맨 마지막(9번째) 인자**다:

```java
		service = new TeamLiveEventPushService(
				deviceRepository,
				deliveryRepository,
				mock(TeamExternalIdentityRepository.class),
				mock(LeagueMatchRepository.class),
				pushGateway,
				notificationService,
				mock(WorldsService.class),
				mock(NaverEsportsScoreClient.class),
				quietAwarePushSender);
```

파일 안 4곳의 `when(pushGateway.send(any(), any()))` 를 `when(quietAwarePushSender.send(any(), any()))` 로,
`verify(pushGateway, ...)` 발송 검증을 `verify(quietAwarePushSender, ...)` 로 바꾼다.
토큰 캡처는 인자 타입이 `Map<Long, List<String>>` 으로 바뀐다:

```java
		ArgumentCaptor<Map<Long, List<String>>> byMember = ArgumentCaptor.forClass(Map.class);
		verify(quietAwarePushSender, times(1)).send(byMember.capture(), any());
		assertThat(byMember.getValue().values().stream().flatMap(List::stream).toList())
				.containsExactly("token-1", "token-2", "token-3");
```

그리고 위임 검증 테스트를 추가한다. 기대 맵과 `any()` 를 한 `verify` 에 섞으면 Mockito 가
`InvalidUseOfMatchersException` 을 던지므로 캡처로 검증한다:

```java
	@Test
	@DisplayName("발송은 회원별 토큰맵으로 sender 에 위임하고 게이트웨이를 직접 부르지 않는다")
	void 발송은_sender_에_위임한다() {
		givenSubscribers(List.of(device(1L, "token-1"), device(2L, "token-2")));
		when(deliveryRepository.reserveAll(any(), anyString(), anyInt(), anyString(), anyLong()))
				.thenReturn(List.of(1L, 2L));
		when(quietAwarePushSender.send(any(), any()))
				.thenReturn(new MobilePushResult(2, 0, List.of(), List.of("token-1", "token-2")));

		service.notifyLiveEvent(MATCH_ID, SET_NUMBER, 14L, null, "제목", "본문");

		ArgumentCaptor<Map<Long, List<String>>> byMember = ArgumentCaptor.forClass(Map.class);
		verify(quietAwarePushSender, times(1)).send(byMember.capture(), any());
		assertThat(byMember.getValue())
				.containsExactlyInAnyOrderEntriesOf(Map.of(1L, List.of("token-1"), 2L, List.of("token-2")));
		verify(pushGateway, never()).send(any(), any());
	}
```

import 추가: `java.util.Map`.

Run: `./gradlew test --tests "com.toy.nar.app.mobile.push.TeamLiveEventPushFanOutBatchTest"`
Expected: 컴파일 실패 — 생성자 인자 개수 불일치(아직 서비스에 필드가 없다)

- [ ] **Step 2: 나머지 두 테스트의 생성자 인자 추가**

`TeamLiveEventPushServiceBestOfTest.java:64-67` 과 `TeamLiveEventPushServiceScoreLineTest.java:55-58` 의
생성자 호출 마지막에 인자 하나를 더한다. 이 두 파일은 발송 횟수를 검증하지 않으므로 목만 넘기면 된다.

```java
		service = new TeamLiveEventPushService(
				deviceRepository, deliveryRepository, teamExternalIdentityRepository,
				leagueMatchRepository, pushGateway, notificationService, worldsService,
				naverEsportsScoreClient, mock(QuietAwarePushSender.class));
```

`BestOfTest` 는 발송 결과를 쓰므로, 기존에 `pushGateway.send` 를 stub 하고 있으면 목을 필드로 빼서
`quietAwarePushSender.send` 를 같은 값으로 stub 한다. `./gradlew test` 결과를 보고 판단한다.

- [ ] **Step 3: 서비스 배선**

`TeamLiveEventPushService.java` 필드 선언 **맨 뒤**(`naverEsportsScoreClient` 다음, `:59` 아래)에 추가한다.
중간에 끼우면 테스트 3개의 인자 순서를 다시 맞춰야 한다.

```java
	private final QuietAwarePushSender quietAwarePushSender;
```

`fanOutBatched` 의 발송부(`:384-387`)를 교체한다. 교체 전:

```java
		List<String> allTokens = tokensByMember.values().stream().flatMap(List::stream).toList();
		MobilePushResult result;
		try {
			result = pushGateway.send(allTokens, message);
```

교체 후:

```java
		List<String> allTokens = tokensByMember.values().stream().flatMap(List::stream).toList();
		MobilePushResult result;
		try {
			// 잠자기 회원은 무음으로 갈린다. 회원별로 쪼개지 않고 2그룹 멀티캐스트.
			result = quietAwarePushSender.send(tokensByMember, message);
```

`allTokens` 는 아래 로그에서 계속 쓰므로 남긴다.

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "com.toy.nar.app.mobile.push.*"`
Expected: 전부 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/toy/nar/app/mobile/push/TeamLiveEventPushService.java \
        src/test/java/com/toy/nar/app/mobile/push/TeamLiveEventPush*.java
git commit -m "$(cat <<'EOF'
feat: 경기 알림 푸시에 알림 잠자기 적용

잠자기는 알림 종류를 가리지 않는다 — "정한 시간엔 조용하다" 한 문장이 유저가
이해할 유일한 모델이다. LCK 정규시즌(17~22시)은 기본 잠자기(01~08시)와 겹치지
않고, 겹치는 국제전 새벽 경기는 유저가 시간을 조정한다.

배치 멀티캐스트는 유지 — 회원별 발송은 이벤트당 8~18분 팬아웃 회귀다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: 설정 조회·저장 API

**Files:**
- Create: `src/main/java/com/toy/nar/app/mobile/notification/dto/QuietHoursResponse.java`
- Create: `src/main/java/com/toy/nar/app/mobile/notification/dto/QuietHoursUpdateRequest.java`
- Create: `src/main/java/com/toy/nar/app/mobile/notification/MobileQuietHoursService.java`
- Create: `src/main/java/com/toy/nar/api/mobile/notification/MobileQuietHoursController.java`
- Modify: `src/main/java/com/toy/nar/common/error/ErrorCode.java`
- Test: `src/test/java/com/toy/nar/app/mobile/notification/MobileQuietHoursServiceTest.java`

**Interfaces:**
- Consumes: `Member#updateQuietHours`, `Member#isQuietHoursEnabled`, `Member#getQuietStartTime`, `Member#getQuietEndTime` (Task 1)
- Produces:
  - `GET /api/mobile/me/quiet-hours` → `QuietHoursResponse`
  - `PUT /api/mobile/me/quiet-hours` ← `QuietHoursUpdateRequest` → `QuietHoursResponse`
  - `ErrorCode#INVALID_QUIET_HOURS`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/toy/nar/app/mobile/notification/MobileQuietHoursServiceTest.java`:

```java
package com.toy.nar.app.mobile.notification;

import com.toy.nar.app.mobile.notification.dto.QuietHoursResponse;
import com.toy.nar.app.mobile.notification.dto.QuietHoursUpdateRequest;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileQuietHoursServiceTest {

	@Mock
	private MemberRepository memberRepository;

	private MobileQuietHoursService service;
	private Member member;

	@BeforeEach
	void setUp() {
		service = new MobileQuietHoursService(memberRepository);
		member = Member.builder().name("테스터").tag("0001").build();
	}

	@Test
	void 잠자기_설정을_저장한다() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		QuietHoursResponse response = service.update(
				1L, new QuietHoursUpdateRequest(true, LocalTime.of(23, 30), LocalTime.of(8, 0)));

		assertThat(response.enabled()).isTrue();
		assertThat(response.startTime()).isEqualTo(LocalTime.of(23, 30));
		assertThat(response.endTime()).isEqualTo(LocalTime.of(8, 0));
		assertThat(member.isQuietHoursEnabled()).isTrue();
	}

	@Test
	void 시작과_종료가_같으면_거부한다() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		assertThatThrownBy(() -> service.update(
				1L, new QuietHoursUpdateRequest(true, LocalTime.of(1, 0), LocalTime.of(1, 0))))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUIET_HOURS);
	}

	@Test
	void 분이_5의_배수가_아니면_거부한다() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		assertThatThrownBy(() -> service.update(
				1L, new QuietHoursUpdateRequest(true, LocalTime.of(1, 3), LocalTime.of(8, 0))))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUIET_HOURS);
	}

	@Test
	void 꺼진_상태로_저장할_때는_시각을_검증하지_않는다() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		QuietHoursResponse response = service.update(
				1L, new QuietHoursUpdateRequest(false, LocalTime.of(1, 0), LocalTime.of(1, 0)));

		assertThat(response.enabled()).isFalse();
	}
}
```

`CustomException` 의 필드명이 `errorCode` 가 아니면(`getErrorCode()` 게터명 확인) `hasFieldOrPropertyWithValue` 인자를 실제 이름으로 고친다.

Run: `./gradlew test --tests "com.toy.nar.app.mobile.notification.MobileQuietHoursServiceTest"`
Expected: 컴파일 실패 — `cannot find symbol: class MobileQuietHoursService`

- [ ] **Step 2: ErrorCode 추가**

`ErrorCode.java` 의 400 블록(`INVALID_MATCH_ID` 다음 줄)에 추가:

```java
	INVALID_QUIET_HOURS(HttpStatus.BAD_REQUEST, "알림 잠자기 시간이 올바르지 않습니다."),
```

- [ ] **Step 3: DTO 작성**

`src/main/java/com/toy/nar/app/mobile/notification/dto/QuietHoursResponse.java`:

```java
package com.toy.nar.app.mobile.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/** 알림 잠자기 설정. 시각은 "HH:mm" 문자열로 주고받는다. */
public record QuietHoursResponse(
		boolean enabled,
		@JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm") LocalTime endTime) {
}
```

`src/main/java/com/toy/nar/app/mobile/notification/dto/QuietHoursUpdateRequest.java`:

```java
package com.toy.nar.app.mobile.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record QuietHoursUpdateRequest(
		boolean enabled,
		@NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
}
```

- [ ] **Step 4: 서비스 작성**

`src/main/java/com/toy/nar/app/mobile/notification/MobileQuietHoursService.java`:

```java
package com.toy.nar.app.mobile.notification;

import com.toy.nar.app.mobile.notification.dto.QuietHoursResponse;
import com.toy.nar.app.mobile.notification.dto.QuietHoursUpdateRequest;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class MobileQuietHoursService {

	private final MemberRepository memberRepository;

	@Transactional(readOnly = true)
	public QuietHoursResponse get(Long memberId) {
		return toResponse(findMember(memberId));
	}

	@Transactional
	public QuietHoursResponse update(Long memberId, QuietHoursUpdateRequest request) {
		Member member = findMember(memberId);
		if (request.enabled()) {
			validate(request.startTime(), request.endTime());
		}
		member.updateQuietHours(request.enabled(), request.startTime(), request.endTime());
		return toResponse(member);
	}

	/**
	 * 시작과 종료가 같으면 판정식이 "24시간 무음" 으로 빠지는데 유저는 그걸 의도하지 않았고
	 * 증상이 조용해서 원인을 못 찾는다. 그래서 거부한다.
	 * 분은 앱이 5분 스텝으로 고르므로 계약만 확인한다.
	 */
	private void validate(LocalTime startTime, LocalTime endTime) {
		if (startTime.equals(endTime)) {
			throw new CustomException(ErrorCode.INVALID_QUIET_HOURS);
		}
		if (startTime.getMinute() % 5 != 0 || endTime.getMinute() % 5 != 0) {
			throw new CustomException(ErrorCode.INVALID_QUIET_HOURS);
		}
	}

	/**
	 * 같은 패키지 {@code MobileTeamNotificationService:118-123} 와 동일한 처리다.
	 * {@code ErrorCode} 에 회원 미존재 상수가 없어 형제 서비스 패턴을 따른다.
	 */
	private Member findMember(Long memberId) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
	}

	private QuietHoursResponse toResponse(Member member) {
		return new QuietHoursResponse(
				member.isQuietHoursEnabled(),
				member.getQuietStartTime(),
				member.getQuietEndTime());
	}
}
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew test --tests "com.toy.nar.app.mobile.notification.MobileQuietHoursServiceTest"`
Expected: 4개 테스트 PASS

- [ ] **Step 6: 컨트롤러 작성**

`src/main/java/com/toy/nar/api/mobile/notification/MobileQuietHoursController.java`:

```java
package com.toy.nar.api.mobile.notification;

import com.toy.nar.app.mobile.notification.MobileQuietHoursService;
import com.toy.nar.app.mobile.notification.dto.QuietHoursResponse;
import com.toy.nar.app.mobile.notification.dto.QuietHoursUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 알림 잠자기", description = "정한 시간대에 푸시를 소리 없이 받는 설정")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/mobile/me/quiet-hours")
@RequiredArgsConstructor
public class MobileQuietHoursController {

	private final MobileQuietHoursService quietHoursService;

	@Operation(summary = "내 알림 잠자기 설정 조회")
	@GetMapping
	public ResponseEntity<QuietHoursResponse> get(@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(quietHoursService.get(memberId));
	}

	@Operation(summary = "내 알림 잠자기 설정 변경")
	@PutMapping
	public ResponseEntity<QuietHoursResponse> update(
			@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody QuietHoursUpdateRequest request) {
		return ResponseEntity.ok(quietHoursService.update(memberId, request));
	}
}
```

- [ ] **Step 7: 인증 경로 허용 확인**

`/api/mobile/me/**` 가 이미 인증 필요 경로로 잡혀 있는지 확인한다.

Run: `grep -rn "api/mobile" src/main/java/com/toy/nar/config/*Security*.java`
Expected: `/api/mobile/me/**` 가 authenticated 로 잡혀 있거나, 기본이 authenticated 라 별도 설정이 없다. `permitAll` 목록에 들어 있으면 안 된다 — 들어 있으면 `/api/mobile/me/**` 를 제외한다.

- [ ] **Step 8: 전체 테스트**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. 로컬 MySQL이 필요한 통합 테스트가 있으니 `docker-compose up -d` 를 먼저 한다. DB 없이 돌려서 통합 테스트가 깨지면, 그 실패가 이 변경과 무관함을 확인하고(`git stash` 후 같은 실패 재현) 사용자에게 보고한다.

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/toy/nar/app/mobile/notification/ \
        src/main/java/com/toy/nar/api/mobile/notification/MobileQuietHoursController.java \
        src/main/java/com/toy/nar/common/error/ErrorCode.java \
        src/test/java/com/toy/nar/app/mobile/notification/MobileQuietHoursServiceTest.java
git commit -m "$(cat <<'EOF'
feat: 알림 잠자기 설정 조회·저장 API

GET/PUT /api/mobile/me/quiet-hours. 시각은 "HH:mm" 문자열.

시작 == 종료를 거부한다. 허용하면 판정식이 24시간 무음으로 빠지는데 유저는
그걸 의도하지 않았고 증상이 조용해서 원인을 못 찾는다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## 완료 후 남는 것

이 계획을 끝내도 **유저에게는 아무 변화가 없다.** `quiet_hours_enabled` 기본값이 0이고 설정 UI가 없어
켤 방법이 없다. 의도한 상태다 — 트렁크가 항상 배포 가능해야 하므로 미완성 기능을 OFF로 머지한다.

후속 작업 2개:

1. **플러터 (`warding-mobile-repo`)** — 별도 계획. `warding_quiet` 채널 생성, 포그라운드 표시 경로 분기
   (`fcm_service.dart:195` 가 `Importance.high` 하드코딩), 마이페이지 설정 카드, `showTimePicker` 대신
   `AppBottomSheet` + 5분 스텝 휠, `app_ko.arb`/`app_en.arb`. 목업 참조.
2. **실기기 검증 경로** — 무음이 진짜 조용한지는 실기기로만 확인된다. 현재 백오피스에 테스트 푸시
   트리거가 **없다**(`grep -rn "testPush\|sendTest" src/main/java/com/toy/nar/api/admin/` → 0건).
   로컬은 FCM 자격증명이 prod 와 공유돼 테스트 푸시가 실유저에게 갈 수 있으므로
   (`CLAUDE.md` 스케줄러 전역 스위치 항목), 내 기기 토큰만 지정해 쏘는 백오피스 API가 필요하다.
   `Clock` 을 빈으로 뺐으므로 잠자기 시각 판정은 테스트에서 고정할 수 있다.
