package com.toy.nar.api.admin;

import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백오피스 조회 전용 API. {@code /api/admin/**} 는 SecurityConfig 에서 ROLE_ADMIN 으로 보호된다.
 * 응답은 Spring {@link Page} 형식({@code content}, {@code totalElements}) 그대로 — FE 데이터프로바이더가 흡수한다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class BackofficeController {

    private final MemberRepository memberRepository;
    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    @GetMapping("/members")
    public Page<MemberRow> members(Pageable pageable) {
        return memberRepository.findAll(pageable)
                .map(m -> new MemberRow(m.getId(), m.getNickname(), m.getEmail(),
                        m.getFavoriteLeagueName(), m.getCreatedAt()));
    }

    @GetMapping("/players")
    public Page<PlayerRow> players(Pageable pageable) {
        return playerRepository.findAll(pageable)
                .map(p -> new PlayerRow(p.getId(), p.getName(), p.getRealName(),
                        p.getRole(), p.getAge()));
    }

    @GetMapping("/teams")
    public Page<TeamRow> teams(Pageable pageable) {
        return teamRepository.findAll(pageable)
                .map(t -> new TeamRow(t.getId(), t.getName(), t.getCode()));
    }

    @GetMapping("/cron-jobs")
    public List<CronJob> cronJobs() {
        return CRON_CATALOG;
    }

    public record MemberRow(Long id, String name, String email,
                            String favoriteLeagueName, LocalDateTime createdAt) {}

    public record PlayerRow(Long id, String name, String realName,
                            String role, Integer age) {}

    public record TeamRow(Long id, String name, String code) {}

    /**
     * @param type        CRON(달력 기준) | INTERVAL(간격 기준)
     * @param schedule    사람이 읽는 주기 (예: "매일 09:00", "60초마다")
     * @param expression  원본 표기 (cron 식 또는 fixedDelay/fixedRate)
     */
    public record CronJob(String name, String type, String schedule, String expression, String description) {}

    // ponytail: @Scheduled 작업 정적 카탈로그. 16개라 잘 안 바뀜.
    // 실행 성공/실패 이력까지 필요해지면 SchedulerAlertService 저장소 연결, 존재/스케줄 자동추적이 필요하면 ScheduledTaskHolder 도입.
    private static final List<CronJob> CRON_CATALOG = List.of(
            new CronJob("sendDailySummary", "CRON", "매일 09:00", "0 0 9 * * * (KST)", "전일 스케줄러 작업 통계 일일 요약 전송"),
            new CronJob("pollRankedSoloPlayers", "INTERVAL", "60초마다", "fixedDelay 60s", "Riot API 랭크 솔로 추적 선수 모니터링"),
            new CronJob("syncAllLeagues", "CRON", "6시간마다", "0 0 */6 * * *", "전체 리그 경기 데이터 동기화"),
            new CronJob("syncTeamMetadataDaily", "CRON", "매일 04:15", "0 15 4 * * *", "팀 메타데이터 일일 동기화"),
            new CronJob("reconcile", "INTERVAL", "60초마다", "fixedDelay 60s", "라이브/배치 게임 데이터 재조정"),
            new CronJob("discoverLiveGames", "INTERVAL", "60초마다", "fixedDelay 60s", "진행 중 라이브 게임 발견"),
            new CronJob("pollLiveGames", "INTERVAL", "5초마다", "fixedDelay 5s", "라이브 게임 상태 폴링"),
            new CronJob("scheduleRecentCommentsSync", "CRON", "매시 정각", "0 0 * * * *", "유튜브 최근 24h 영상 댓글 동기화"),
            new CronJob("scheduleRecentThreeHoursVideosStatsSync", "CRON", "10분마다", "0 0/10 * * * *", "유튜브 최근 3h 영상 통계 갱신"),
            new CronJob("scheduleRecentDayVideosStatsSync", "CRON", "매시 정각", "0 0 * * * *", "유튜브 최근 24h 영상 통계 갱신"),
            new CronJob("scheduleLastWeekVideosSync", "CRON", "매일 03:00", "0 0 3 * * * (KST)", "유튜브 주간 영상/통계 동기화"),
            new CronJob("syncCommunityData", "CRON", "10분마다", "0 0/10 * * * *", "커뮤니티/뉴스 데이터 동기화"),
            new CronJob("cleanupOldData", "CRON", "매일 04:00", "0 0 4 * * *", "7일 지난 커뮤니티 게시글 삭제"),
            new CronJob("refreshSubscription", "CRON", "매일 04:00", "0 0 4 * * *", "유튜브 PubSub 구독 갱신"),
            new CronJob("scheduledSyncFromGoogleDrive", "CRON", "매일 04:30/10:30/16:30/22:30", "0 30 4,10,16,22 * * ? (KST)", "Google Drive CSV 경기 데이터 동기화"),
            new CronJob("checkUserCountAndNotify", "INTERVAL", "60초마다", "fixedRate 60s", "실시간 접속자 수 모니터링")
    );
}
