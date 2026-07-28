package com.toy.nar.api.admin;

import com.toy.nar.app.lolesports.LeagueConfigService;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.lolesports.repository.LeagueConfig;
import com.toy.nar.app.member.service.MemberDeleteService;
import com.toy.nar.app.participant.service.PlayerAdminService;
import com.toy.nar.app.participant.service.PlayerImageStorageService;
import com.toy.nar.app.riot.RiotApiException;
import com.toy.nar.domain.game.repository.LeagueRepository;
import com.toy.nar.domain.member.repository.MemberFavoritePlayerRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.MemberTeamNotificationSubscriptionRepository;
import com.toy.nar.domain.participant.LckTeamCatalog;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import com.toy.nar.domain.rating.entity.LivePlayerRating;
import com.toy.nar.domain.rating.repository.LivePlayerRatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 백오피스 API. {@code /api/admin/**} 는 SecurityConfig 에서 ROLE_ADMIN 으로 보호된다.
 * 조회 응답은 Spring {@link Page} 형식({@code content}, {@code totalElements}) 그대로 — FE 데이터프로바이더가 흡수한다.
 * 쓰기: 리그 설정 토글, LCK 선수 수정(PUT /players/{id}), 회원/선수/팀 삭제(DELETE).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class BackofficeController {

    private final MemberRepository memberRepository;
    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueConfigService leagueConfigService;
    private final PlayerAdminService playerAdminService;
    private final PlayerImageStorageService playerImageStorageService;
    private final MemberDeleteService memberDeleteService;
    private final LivePlayerRatingRepository livePlayerRatingRepository;
    private final LeagueMatchRepository leagueMatchRepository;
    private final MemberFavoritePlayerRepository memberFavoritePlayerRepository;
    private final MemberTeamNotificationSubscriptionRepository teamSubscriptionRepository;

    @GetMapping("/members")
    public Page<MemberRow> members(@RequestParam(required = false) String q, Pageable pageable) {
        return memberRepository.searchForBackoffice(blankToNull(q), pageable)
                .map(m -> new MemberRow(m.getId(), m.getNickname(), m.getEmail(),
                        m.getFavoriteLeagueName(), m.getCreatedAt()));
    }

    @GetMapping("/players")
    public Page<PlayerRow> players(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) String league,
                                   Pageable pageable) {
        // 리그 유무로 쿼리 분리 — "(:league IS NULL OR EXISTS)" 단일 쿼리는 semijoin이 막혀 1.7초/쿼리.
        String leagueParam = blankToNull(league);
        Page<com.toy.nar.domain.participant.entity.Player> page = (leagueParam == null)
                ? playerRepository.searchForBackoffice(blankToNull(q), pageable)
                : playerRepository.searchForBackofficeInLeague(blankToNull(q), leagueParam, pageable);
        return page.map(PlayerRow::from);
    }

    @GetMapping("/teams")
    public Page<TeamRow> teams(@RequestParam(required = false) String q,
                               @RequestParam(required = false) String league,
                               Pageable pageable) {
        String leagueParam = blankToNull(league);
        Page<com.toy.nar.domain.participant.entity.Team> page = (leagueParam == null)
                ? teamRepository.searchForBackoffice(blankToNull(q), pageable)
                : teamRepository.searchForBackofficeInLeague(blankToNull(q), leagueParam, pageable);
        return page.map(t -> new TeamRow(t.getId(), t.getName(), t.getCode()));
    }

    // 리그 필터 옵션. 실제 데이터에 존재하는 리그명(전 시즌) distinct.
    @GetMapping("/leagues")
    public List<String> leagues() {
        return leagueRepository.findAllDistinctLeagueNames();
    }

    // 구독 탭 — 구독 가능한 선수 목록(구독자 수 desc). q로 선수명 검색.
    // 정렬은 쿼리에 고정(인기순)이라 클라이언트 sort는 무시(page/size만 사용).
    @GetMapping("/subscriptions/players")
    public Page<SubscribablePlayerRow> subscribablePlayers(@RequestParam(required = false) String q,
                                                           Pageable pageable) {
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return playerRepository.findSubscribablePlayers(blankToNull(q), pageOnly)
                .map(v -> new SubscribablePlayerRow(v.getPlayerId(), v.getPlayerName(), v.getImageUrl(),
                        v.getRole(), v.getTeamId(), v.getTeamName(), v.getRiotId(), v.getPlatform(),
                        v.getSubscriberCount()));
    }

    // 구독 탭 — 특정 선수를 구독한 회원 목록(최근순).
    @GetMapping("/subscriptions/players/{playerId}/subscribers")
    public Page<SubscriberRow> playerSubscribers(@PathVariable Long playerId, Pageable pageable) {
        return memberFavoritePlayerRepository.findSubscribersByPlayerId(playerId, pageable)
                .map(v -> new SubscriberRow(v.getMemberId(), v.getName() + "#" + v.getTag(),
                        v.getEmail(), v.getSubscribedAt()));
    }

    // 구독 탭 — 구독 가능한 팀 목록(LCK 카탈로그, 구독자 수 desc). q로 팀명·코드 검색.
    // 정렬은 쿼리에 고정(인기순)이라 클라이언트 sort는 무시(page/size만 사용).
    @GetMapping("/subscriptions/teams")
    public Page<SubscribableTeamRow> subscribableTeams(@RequestParam(required = false) String q,
                                                       Pageable pageable) {
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return teamRepository.findSubscribableTeams(LckTeamCatalog.TEAM_CODES, blankToNull(q), pageOnly)
                .map(v -> new SubscribableTeamRow(v.getTeamId(), v.getTeamName(), v.getTeamCode(),
                        v.getImageUrl(), v.getSubscriberCount()));
    }

    // 구독 탭 — 특정 팀을 구독한 회원 목록(최근순) + 알림 토글 상태.
    // field(memberId|nickname|email)+q 로 검색. field 없이 q만 오면 닉네임 기준.
    @GetMapping("/subscriptions/teams/{teamId}/subscribers")
    public Page<TeamSubscriberRow> teamSubscribers(@PathVariable Long teamId,
                                                   @RequestParam(required = false) String field,
                                                   @RequestParam(required = false) String q,
                                                   Pageable pageable) {
        String fieldParam = blankToNull(field);
        return teamSubscriptionRepository.findSubscribersByTeamId(
                        teamId, fieldParam == null ? "nickname" : fieldParam, blankToNull(q), pageable)
                .map(v -> new TeamSubscriberRow(v.getMemberId(), v.getName() + "#" + v.getTag(),
                        v.getEmail(), v.getSubscribedAt(), v.getSetStartEnabled(),
                        v.getSetEndEnabled(), v.getLiveEventEnabled()));
    }

    // 빈 문자열/공백은 null 로 정규화 → 검색 쿼리의 ":q IS NULL" 분기가 전체 조회로 동작.
    private static String blankToNull(String q) {
        return (q == null || q.isBlank()) ? null : q.trim();
    }

    // 회원이 모바일에서 작성한 선수 리뷰(별점 + 한줄평). 부적절한 한줄평 삭제용.
    // 경기 정보(리그·팀·일시)는 rating.matchId = league_match.id 로 페이지 단위 배치 조회해 붙인다.
    // field(player|member|comment|all)+q 로 검색. field 없이 q만 오면 전체 대상(all).
    @GetMapping("/ratings")
    public Page<RatingRow> ratings(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) String field,
                                   @RequestParam(required = false) Integer rating,
                                   Pageable pageable) {
        String fieldParam = blankToNull(field);
        Page<LivePlayerRating> page = livePlayerRatingRepository.searchForBackoffice(
                blankToNull(q), fieldParam == null ? "all" : fieldParam, rating, pageable);
        Set<String> matchIds = page.getContent().stream()
                .map(LivePlayerRating::getMatchId)
                .collect(Collectors.toSet());
        Map<String, LeagueMatch> matches = leagueMatchRepository.findAllById(matchIds).stream()
                .collect(Collectors.toMap(LeagueMatch::getId, m -> m));
        return page.map(r -> RatingRow.from(r, matches.get(r.getMatchId())));
    }

    @DeleteMapping("/ratings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRating(@PathVariable Long id) {
        if (!livePlayerRatingRepository.existsById(id)) {
            throw new NoSuchElementException("리뷰를 찾을 수 없습니다: " + id);
        }
        livePlayerRatingRepository.deleteById(id);
    }

    @GetMapping("/cron-jobs")
    public List<CronJob> cronJobs() {
        return CRON_CATALOG;
    }

    @GetMapping("/league-configs")
    public List<LeagueConfigRow> leagueConfigs() {
        return leagueConfigService.findAll().stream().map(LeagueConfigRow::from).toList();
    }

    @PutMapping("/league-configs/{leagueName}")
    public LeagueConfigRow updateLeagueConfig(@PathVariable String leagueName,
                                              @RequestBody LeagueConfigUpdateRequest request) {
        return LeagueConfigRow.from(leagueConfigService.update(
                leagueName, request.liveEnabled(), request.notificationEnabled(), request.syncEnabled()));
    }

    // LCK 선수 한정 수정(이미지 = 수동 잠금 동반, 소속팀 변경, 솔랭 계정 수동 잠금). 서버에서 LCK 출전 이력 검증.
    @PutMapping("/players/{id}")
    public PlayerRow updatePlayer(@PathVariable Long id, @RequestBody PlayerUpdateRequest request) {
        return PlayerRow.from(playerAdminService.update(
                id, request.imageUrl(), request.unlockImage(), request.currentTeamId(),
                request.unlockGameAccounts(), request.gameAccounts()));
    }

    // 선수 이미지 업로드(배포 불필요). 파일은 컨테이너 밖 디렉토리에 저장하고 DB엔 경로만 남긴다.
    // PUT /players/{id} 와 달리 LCK 게이트가 없다 — LCK CL·LCS 선수도 이미지가 필요하다.
    @PostMapping("/players/{id}/image")
    public PlayerRow uploadPlayerImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return PlayerRow.from(playerImageStorageService.upload(id, file));
    }

    // 솔랭 전용 선수 등록(은퇴/비현역). LCK 출전 이력 없이 이름+riotId로 생성.
    @PostMapping("/players/solo-rank")
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerRow createSoloRankPlayer(@RequestBody SoloRankPlayerCreateRequest request) {
        return PlayerRow.from(playerAdminService.createSoloRankPlayer(
                request.name(), request.imageUrl(), request.riotId(), request.region()));
    }

    // 기존 선수(비-LCK 포함)에 솔랭 계정 부착/교체. 해외 이적 선수 KR→EUW 교체도 이 경로.
    @PostMapping("/players/{id}/solo-rank-account")
    public PlayerRow attachSoloRankAccount(@PathVariable Long id, @RequestBody SoloRankAccountRequest request) {
        return PlayerRow.from(playerAdminService.attachSoloRankAccount(
                id, request.riotId(), request.region(), request.imageUrl()));
    }

    @DeleteMapping("/members/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMember(@PathVariable Long id) {
        memberDeleteService.delete(id);
    }

    // 선수/팀은 경기 기록(game_participants) FK가 걸리면 삭제 불가 → DataIntegrityViolation → 409.
    @DeleteMapping("/players/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlayer(@PathVariable Long id) {
        if (!playerRepository.existsById(id)) {
            throw new NoSuchElementException("선수를 찾을 수 없습니다: " + id);
        }
        playerRepository.deleteById(id);
    }

    @DeleteMapping("/teams/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTeam(@PathVariable Long id) {
        if (!teamRepository.existsById(id)) {
            throw new NoSuchElementException("팀을 찾을 수 없습니다: " + id);
        }
        teamRepository.deleteById(id);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> onConflict() {
        return Map.of("message", "경기 기록 등 연관 데이터가 있어 삭제할 수 없습니다");
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> onNotFound(NoSuchElementException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> onBadRequest(IllegalStateException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> onInvalidArgument(IllegalArgumentException e) {
        return Map.of("message", e.getMessage());
    }

    // Riot 실검증 실패: 계정 없음(404)은 어드민 입력 오류(400), 그 외(키 미설정/장애)는 502.
    @ExceptionHandler(RiotApiException.class)
    public ResponseEntity<Map<String, String>> onRiotApiError(RiotApiException e) {
        if (e.getStatusCode() == 404) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "존재하지 않는 Riot ID입니다. 이름#태그를 확인해 주세요"));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "Riot API 오류로 저장하지 못했습니다. 잠시 후 다시 시도해 주세요"));
    }

    public record MemberRow(Long id, String name, String email,
                            String favoriteLeagueName, LocalDateTime createdAt) {}

    // 구독 탭 — 구독 가능 선수 행(구독자 수 포함). id 필드는 FE 데이터그리드 rowKey 용.
    public record SubscribablePlayerRow(Long id, String playerName, String imageUrl, String role,
                                        Long teamId, String teamName, String riotId, String platform,
                                        long subscriberCount) {}

    // 구독 탭 — 구독자 행. id 필드는 FE rowKey 용(memberId).
    public record SubscriberRow(Long id, String nickname, String email, LocalDateTime subscribedAt) {}

    public record SubscribableTeamRow(Long id, String teamName, String teamCode, String imageUrl,
                                      long subscriberCount) {}

    public record TeamSubscriberRow(Long id, String nickname, String email, LocalDateTime subscribedAt,
                                    boolean setStartEnabled, boolean setEndEnabled,
                                    boolean liveEventEnabled) {}

    public record PlayerRow(Long id, String name, String realName, String role, Integer age,
                            String imageUrl, Long currentTeamId, String currentTeamName, boolean imageLocked,
                            String gameAccounts, boolean gameAccountsLocked) {
        static PlayerRow from(Player p) {
            var team = p.getCurrentTeam();
            return new PlayerRow(p.getId(), p.getName(), p.getRealName(), p.getRole(), p.getAge(),
                    p.getImageUrl(), team != null ? team.getId() : null,
                    team != null ? team.getName() : null, p.isImageLocked(),
                    p.getGameAccounts(), p.isGameAccountsLocked());
        }
    }

    public record GameAccountEntry(String region, String riotId, String tier) {}

    public record PlayerUpdateRequest(String imageUrl, Boolean unlockImage, Long currentTeamId,
                                      Boolean unlockGameAccounts, List<GameAccountEntry> gameAccounts) {}

    // region 미지정 시 KR. 해외 선수는 EUW/NA 등 지정(또는 EUW1/NA1 플랫폼 값).
    public record SoloRankPlayerCreateRequest(String name, String imageUrl, String riotId, String region) {}

    // 기존 선수 id에 솔랭 계정 부착. region 미지정 시 KR. imageUrl 있으면 함께 세팅.
    public record SoloRankAccountRequest(String riotId, String region, String imageUrl) {}

    public record TeamRow(Long id, String name, String code) {}

    /**
     * @param matchDate 경기 일시. league_match 는 UTC 로 저장하므로 모바일 응답과 동일하게 KST 로 변환해 내린다.
     *                  매치 정보를 못 찾으면(동기화 전/삭제) 경기 관련 필드는 null.
     */
    public record RatingRow(Long id, String matchId, String leagueName, String matchTitle,
                            String blueTeamCode, String redTeamCode, LocalDateTime matchDate,
                            String playerName, String championName, String role,
                            String memberNickname, Integer rating, String comment,
                            LocalDateTime createdAt) {
        static RatingRow from(LivePlayerRating r, LeagueMatch match) {
            return new RatingRow(r.getId(), r.getMatchId(),
                    match != null ? match.getLeagueName() : null,
                    match != null ? match.getMatchTitle() : null,
                    match != null ? match.getBlueTeamCode() : null,
                    match != null ? match.getRedTeamCode() : null,
                    match != null ? toKst(match.getMatchDate()) : null,
                    r.getPlayerName(), r.getChampionName(), r.getRole(),
                    r.getMember().getNickname(), r.getRating(), r.getComment(),
                    r.getCreatedAt());
        }

        private static LocalDateTime toKst(LocalDateTime utc) {
            return utc == null ? null
                    : utc.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        }
    }

    public record LeagueConfigRow(String leagueName, boolean liveEnabled,
                                  boolean notificationEnabled, boolean syncEnabled) {
        static LeagueConfigRow from(LeagueConfig config) {
            return new LeagueConfigRow(config.getLeagueName(), config.isLiveEnabled(),
                    config.isNotificationEnabled(), config.isSyncEnabled());
        }
    }

    public record LeagueConfigUpdateRequest(boolean liveEnabled, boolean notificationEnabled,
                                            boolean syncEnabled) {}

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
