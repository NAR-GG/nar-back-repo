package com.toy.nar.app.schedule;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto;
import com.toy.nar.domain.game.repository.BanRepository;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.GameRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
class ScheduleServicePerformanceBaselineTest {

    @Autowired
    private LeagueMatchRepository leagueMatchRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameParticipantRepository gameParticipantRepository;

    @Autowired
    private BanRepository banRepository;

    @Autowired
    private LeagueMatchGameRepository leagueMatchGameRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private ScheduleService scheduleService;
    private SessionFactory sessionFactory;

    @BeforeEach
    void setUp() {
        MatchDetailFinder matchDetailFinder = new MatchDetailFinder(gameParticipantRepository);
        scheduleService = new ScheduleService(
                null,
                null,
                gameRepository,
                banRepository,
                gameParticipantRepository,
                leagueMatchRepository,
                leagueMatchGameRepository,
                matchDetailFinder,
                null);
        sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);
    }

    @Test
    @DisplayName("parseGameDetailsFromLeagueMatch baseline benchmark")
    void benchmarkParseGameDetailsFromLeagueMatch() {
        String league = System.getProperty("benchmark.schedule.league", "LCK");
        String date = System.getProperty("benchmark.schedule.date");
        int sampleSize = Integer.getInteger("benchmark.schedule.sampleSize", 20);

        List<LeagueMatch> matches = loadMatches(league, date, sampleSize);
        if (matches.isEmpty()) {
            throw new IllegalStateException("No league matches found for benchmark input. league=" + league + ", date=" + date);
        }

        entityManager.flush();
        entityManager.clear();
        sessionFactory.getStatistics().clear();

        long startedAt = System.nanoTime();
        int totalGameDetails = 0;
        int syncedMatches = 0;
        for (LeagueMatch match : matches) {
            MatchDetailResponseDto detail = (MatchDetailResponseDto) ReflectionTestUtils.invokeMethod(
                    scheduleService,
                    "convertLeagueMatchToDetailDto",
                    match);
            totalGameDetails += detail.gameDetails().size();
            if (detail.summary().isSynced()) {
                syncedMatches++;
            }
        }
        entityManager.flush();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        Statistics statistics = sessionFactory.getStatistics();
        System.out.println("[Benchmark] parseGameDetailsFromLeagueMatch");
        System.out.println("  league=" + league);
        System.out.println("  date=" + date);
        System.out.println("  sampleSize=" + matches.size());
        System.out.println("  elapsedMs=" + elapsedMs);
        System.out.println("  totalGameDetails=" + totalGameDetails);
        System.out.println("  syncedMatches=" + syncedMatches);
        System.out.println("  prepareStatementCount=" + statistics.getPrepareStatementCount());
        System.out.println("  entityLoadCount=" + statistics.getEntityLoadCount());
        System.out.println("  entityFetchCount=" + statistics.getEntityFetchCount());
        System.out.println("  collectionFetchCount=" + statistics.getCollectionFetchCount());
    }

    private List<LeagueMatch> loadMatches(String league, String date, int sampleSize) {
        if (date == null || date.isBlank()) {
            return leagueMatchRepository.findByLeagueNameOrderByMatchDateDesc(league, PageRequest.of(0, sampleSize)).stream()
                    .filter(this::isCompletedWithDate)
                    .limit(sampleSize)
                    .toList();
        }

        if (date.matches("\\d{4}-\\d{2}")) {
            LocalDate monthStart = LocalDate.parse(date + "-01");
            LocalDateTime start = monthStart.atStartOfDay();
            LocalDateTime end = monthStart.plusMonths(1).atStartOfDay().minusSeconds(1);
            return leagueMatchRepository.findByLeagueNameAndDateRange(league, start, end).stream()
                    .filter(this::isCompletedWithDate)
                    .limit(sampleSize)
                    .toList();
        }

        if (date.matches("\\d{4}")) {
            LocalDate yearStart = LocalDate.parse(date + "-01-01");
            LocalDateTime start = yearStart.atStartOfDay();
            LocalDateTime end = yearStart.plusYears(1).atStartOfDay().minusSeconds(1);
            return leagueMatchRepository.findByLeagueNameAndDateRange(league, start, end).stream()
                    .filter(this::isCompletedWithDate)
                    .limit(sampleSize)
                    .toList();
        }

        throw new IllegalArgumentException("Unsupported benchmark.schedule.date format: " + date);
    }

    private boolean isCompletedWithDate(LeagueMatch match) {
        return match.getMatchDate() != null && "completed".equalsIgnoreCase(match.getState());
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
