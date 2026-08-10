package com.toy.nar.app.lolesports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.domain.game.repository.GameRepository;
import com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * 실데이터가 적재된 로컬 dev MySQL(application-dev.yml) 전용 벤치마크.
 * 클린 체크아웃에는 dev 설정 파일이 없어 H2로 폴백되므로 기본 빌드에서는 건너뛴다.
 * 실행: ./gradlew test -Dbenchmark.local.enabled=true --tests "...PerformanceBaselineTest"
 */
@EnabledIfSystemProperty(named = "benchmark.local.enabled", matches = "true")
@DataJpaTest
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LeagueMatchServicePerformanceBaselineTest {

    @Autowired
    private LeagueMatchRepository leagueMatchRepository;

    @Autowired
    private LeagueMatchGameRepository leagueMatchGameRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamExternalIdentityRepository teamExternalIdentityRepository;

    @Autowired
    private GameRepository gameRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private LeagueMatchService leagueMatchService;
    private SessionFactory sessionFactory;

    @BeforeEach
    void setUp() {
        leagueMatchService = new LeagueMatchService(
                leagueMatchRepository,
                leagueMatchGameRepository,
                null,
                teamRepository,
                teamExternalIdentityRepository,
                null,
                null,
                new ObjectMapper(),
                null,
                gameRepository,
                null,
                null);
        sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);
    }

    @Test
    @DisplayName("updateTeamMetadataFromMatches baseline benchmark")
    void benchmarkUpdateTeamMetadataFromMatches() {
        String league = System.getProperty("benchmark.lolesports.league", "LCK");
        String date = System.getProperty("benchmark.lolesports.date");
        int sampleSize = Integer.getInteger("benchmark.lolesports.sampleSize", 20);

        List<MatchResultDto> matches = leagueMatchService.getMatchesFromDb(league, date).getMatches();
        if (matches.isEmpty()) {
            throw new IllegalStateException("No matches found for benchmark input. league=" + league + ", date=" + date);
        }
        if (sampleSize > 0 && matches.size() > sampleSize) {
            matches = matches.subList(0, sampleSize);
        }

        entityManager.flush();
        entityManager.clear();
        sessionFactory.getStatistics().clear();

        long startedAt = System.nanoTime();
        int updated = leagueMatchService.updateTeamMetadataFromMatches(matches);
        entityManager.flush();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        Statistics statistics = sessionFactory.getStatistics();
        System.out.println("[Benchmark] updateTeamMetadataFromMatches");
        System.out.println("  league=" + league);
        System.out.println("  date=" + date);
        System.out.println("  sampleSize=" + matches.size());
        System.out.println("  elapsedMs=" + elapsedMs);
        System.out.println("  updatedRecords=" + updated);
        System.out.println("  prepareStatementCount=" + statistics.getPrepareStatementCount());
        System.out.println("  entityLoadCount=" + statistics.getEntityLoadCount());
        System.out.println("  entityUpdateCount=" + statistics.getEntityUpdateCount());
        System.out.println("  collectionFetchCount=" + statistics.getCollectionFetchCount());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.toy.nar")
    @EnableJpaRepositories(basePackages = "com.toy.nar", excludeFilters = @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.toy\\.nar\\.domain\\.search\\.repository\\..*"))
    static class TestJpaApplication {
    }
}
