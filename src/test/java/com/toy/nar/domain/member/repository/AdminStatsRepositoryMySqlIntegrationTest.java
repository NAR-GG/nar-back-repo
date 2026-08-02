package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberMatchSubscription;
import com.toy.nar.domain.member.entity.MemberNotification;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import com.toy.nar.domain.member.entity.MemberTeamNotificationSubscription;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대시보드 집계 네이티브 쿼리를 실제 MySQL 8 에서 검증한다.
 * {@code DATE_FORMAT} 시간 버킷, 세 구독 테이블 UNION, {@code FROM dual} 서브쿼리 묶음, {@code LIMIT :limit} 바인딩은
 * H2 나 JPQL 로는 검증이 안 되고 런타임에야 깨지는 것들이라 MySQL 대상 테스트가 필요하다.
 *
 * <p>로컬 dev MySQL(docker-compose, 3308)의 격리 스키마 nar_admin_stats_test 에 ddl-auto=create-drop 으로 실행한다.
 * 사전 준비(최초 1회): CREATE DATABASE nar_admin_stats_test; GRANT ALL ON nar_admin_stats_test.* TO 'nar_id'@'%';
 * 실행: ./gradlew test -Ddataintegrity.local.enabled=true --tests "...AdminStatsRepositoryMySqlIntegrationTest"
 */
@EnabledIfSystemProperty(named = "dataintegrity.local.enabled", matches = "true")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminStatsRepositoryMySqlIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:mysql://localhost:3308/nar_admin_stats_test?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> "nar_id");
        registry.add("spring.datasource.password", () -> "nar_pw");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private AdminStatsRepository repository;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    // 가입 2건은 같은 시간 버킷, 1건은 다른 버킷으로 몰아 넣는다.
    private static final LocalDateTime HOUR_A = LocalDateTime.now().minusDays(1).withHour(13).withMinute(20).withSecond(0).withNano(0);
    private static final LocalDateTime HOUR_B = LocalDateTime.now().minusDays(1).withHour(21).withMinute(5).withSecond(0).withNano(0);
    private static final LocalDateTime FROM = LocalDateTime.now().minusDays(3).withHour(0).withMinute(0).withSecond(0).withNano(0);

    private String bucketA;
    private String bucketB;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM member_favorite_player");
        jdbc.execute("DELETE FROM member_team_notification_subscription");
        jdbc.execute("DELETE FROM member_match_subscription");
        jdbc.execute("DELETE FROM member_notification");
        jdbc.execute("DELETE FROM member");

        Team team = em.persist(Team.builder().name("T1").code("T1").build());
        Player player = em.persist(Player.builder().name("Faker").build());

        Member a = em.persist(Member.builder().name("가입자A").tag("0001").email("a@nar.kr").build());
        Member b = em.persist(Member.builder().name("가입자B").tag("0002").email("b@nar.kr").build());
        Member c = em.persist(Member.builder().name("가입자C").tag("0003").email("c@nar.kr").build());

        // A: 온보딩(=선수 구독 1건 생성) + 팀·경기 구독. B: 온보딩만(관심 선수 없음). C: 아무것도 안 함.
        a.completeOnboarding("LCK", team, List.of(player));
        b.completeOnboarding("LPL", null, List.of());

        em.persist(new MemberTeamNotificationSubscription(a, team));
        em.persist(new MemberMatchSubscription(a, "match-1", true, true, false));
        em.persist(new MemberNotification(a, MemberNotificationType.SET_START, "경기 시작", "T1 vs GEN", Map.of()));
        em.persist(new MemberNotification(b, MemberNotificationType.SET_END, "경기 종료", "T1 승", Map.of()));
        em.flush();

        // 엔티티가 created_at 을 now() 로 박으므로 버킷 검증을 위해 뒤로 돌린다.
        backdate("member", List.of(a.getId(), b.getId()), HOUR_A);
        backdate("member", List.of(c.getId()), HOUR_B);
        jdbc.update("UPDATE member_favorite_player SET created_at = ?", HOUR_A);
        jdbc.update("UPDATE member_team_notification_subscription SET created_at = ?", HOUR_A);
        jdbc.update("UPDATE member_match_subscription SET created_at = ?", HOUR_B);
        jdbc.update("UPDATE member_notification SET created_at = ?", HOUR_B);
        em.clear();

        bucketA = bucketOf(HOUR_A);
        bucketB = bucketOf(HOUR_B);
    }

    private void backdate(String table, List<Long> ids, LocalDateTime at) {
        ids.forEach(id -> jdbc.update("UPDATE " + table + " SET created_at = ? WHERE id = ?", at, id));
    }

    /**
     * 전체 앱을 띄우면 Elasticsearch 빈을 요구해 슬라이스가 뜨지 않는다 — 저장소의 다른 리포지토리 테스트와 같은 방식으로 범위를 좁힌다.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    // 엔티티는 도메인 전체를 스캔한다(participant → game 처럼 서로 물려 있어 일부만 담으면 매핑이 깨진다).
    // 리포지토리 스캔만 member 로 좁혀 Elasticsearch 리포지토리를 피한다.
    @EntityScan(basePackages = "com.toy.nar.domain")
    @EnableJpaRepositories(basePackageClasses = AdminStatsRepository.class)
    static class TestJpaConfiguration {
    }

    private static String bucketOf(LocalDateTime at) {
        return "%04d-%02d-%02dT%02d:00".formatted(at.getYear(), at.getMonthValue(), at.getDayOfMonth(), at.getHour());
    }

    @Test
    void 가입은_시간_버킷으로_묶인다() {
        var rows = repository.signupsByHour(FROM);

        assertThat(rows).extracting(AdminStatsRepository.HourCount::getBucket)
                .containsExactly(bucketA, bucketB);
        assertThat(rows).extracting(AdminStatsRepository.HourCount::getCnt)
                .containsExactly(2L, 1L);
    }

    @Test
    void 구독_세_테이블이_한_버킷으로_합쳐진다() {
        var rows = repository.subscriptionsByHour(FROM);

        assertThat(rows).hasSize(2);
        var first = rows.get(0);
        assertThat(first.getBucket()).isEqualTo(bucketA);
        assertThat(first.getPlayerCnt()).isEqualTo(1);
        assertThat(first.getTeamCnt()).isEqualTo(1);
        assertThat(first.getMatchCnt()).isZero();

        var second = rows.get(1);
        assertThat(second.getBucket()).isEqualTo(bucketB);
        assertThat(second.getMatchCnt()).isEqualTo(1);
    }

    @Test
    void 알림_발송량도_시간_버킷으로_나온다() {
        var rows = repository.notificationsByHour(FROM);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getBucket()).isEqualTo(bucketB);
        assertThat(rows.get(0).getCnt()).isEqualTo(2);
    }

    @Test
    void 시작시각_이전_데이터는_빠진다() {
        assertThat(repository.signupsByHour(LocalDateTime.now().plusDays(1))).isEmpty();
    }

    @Test
    void 퍼널_네_단계가_회원_수로_집계된다() {
        var totals = repository.memberTotals();

        assertThat(totals.getTotalMembers()).isEqualTo(3);
        assertThat(totals.getOnboardedMembers()).isEqualTo(2);
        assertThat(totals.getSubscribedMembers()).isEqualTo(1); // A 는 세 종류 구독해도 1명
        assertThat(totals.getRatedMembers()).isZero();
    }

    @Test
    void 분포_쿼리는_상위부터_정렬해_내려준다() {
        assertThat(repository.membersByFavoriteLeague())
                .extracting(AdminStatsRepository.LabelCount::getLabel)
                .containsExactlyInAnyOrder("LCK", "LPL", "미설정");

        assertThat(repository.topSubscribedTeams(10))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getLabel()).isEqualTo("T1");
                    assertThat(row.getCnt()).isEqualTo(1);
                });

        assertThat(repository.topSubscribedPlayers(10))
                .singleElement()
                .satisfies(row -> assertThat(row.getLabel()).isEqualTo("Faker"));
    }
}
