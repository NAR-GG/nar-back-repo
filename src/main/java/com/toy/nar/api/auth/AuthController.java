package com.toy.nar.api.auth;

import com.toy.nar.api.auth.dto.KakaoMobileLoginRequest;
import com.toy.nar.api.auth.dto.MemberResponse;
import com.toy.nar.api.auth.dto.OnboardingLeagueOptionResponse;
import com.toy.nar.api.auth.dto.OnboardingPlayerOptionResponse;
import com.toy.nar.api.auth.dto.OnboardingRequest;
import com.toy.nar.api.auth.dto.OnboardingTeamOptionResponse;
import com.toy.nar.api.auth.dto.TokenResponse;
import com.toy.nar.app.auth.AuthTokens;
import com.toy.nar.app.auth.JwtTokenProvider;
import com.toy.nar.app.auth.KakaoUserClient;
import com.toy.nar.app.auth.SocialAccountInfo;
import com.toy.nar.app.auth.SocialLoginService;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.RefreshToken;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.RefreshTokenRepository;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "8. 인증 / 로그인", description = "소셜 로그인과 JWT 기반 사용자 인증 API")
public class AuthController {

    private static final int DEFAULT_ONBOARDING_YEAR = 2026;

    private static final List<String> LCK_ONBOARDING_TEAM_CODES = List.of(
            "T1", "HLE", "GEN", "DK", "KT",
            "DNS", "BFX", "NS", "BRO", "KRX"
    );

    private static final List<OnboardingLeagueOptionResponse> ONBOARDING_LEAGUES = List.of(
            new OnboardingLeagueOptionResponse(
                    "LCK",
                    "대한민국",
                    "http://static.lolesports.com/leagues/lck-color-on-black.png"),
            new OnboardingLeagueOptionResponse(
                    "LPL",
                    "중국",
                    "http://static.lolesports.com/leagues/1592516115322_LPL-01-FullonDark.png"),
            new OnboardingLeagueOptionResponse(
                    "LEC",
                    "유럽/중동/아프리카",
                    "http://static.lolesports.com/leagues/1592516184297_LEC-01-FullonDark.png"),
            new OnboardingLeagueOptionResponse(
                    "LCS",
                    "북아메리카",
                    "http://static.lolesports.com/leagues/1706356907418_LCSNew-01-FullonDark.png"),
            new OnboardingLeagueOptionResponse(
                    "LCP",
                    "아시아 태평양",
                    "http://static.lolesports.com/leagues/1733468139601_lcp-color-golden.png"),
            new OnboardingLeagueOptionResponse(
                    "CBLOL",
                    "남아메리카",
                    "http://static.lolesports.com/leagues/cblol-logo-symbol-offwhite.png")
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final KakaoUserClient kakaoUserClient;
    private final SocialLoginService socialLoginService;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    @Operation(
            summary = "모바일 카카오 로그인",
            description = "Flutter Kakao SDK에서 발급받은 카카오 Access Token을 검증하고 서비스 JWT를 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모바일 카카오 로그인 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 카카오 Access Token")
    })
    @PostMapping("/mobile/kakao")
    public ResponseEntity<TokenResponse> loginWithKakaoAccessToken(
            @Valid @RequestBody KakaoMobileLoginRequest request) {
        SocialAccountInfo accountInfo = kakaoUserClient.fetchUser(request.accessToken());
        AuthTokens tokens = socialLoginService.login(accountInfo);
        return ResponseEntity.ok(new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.isOnboarded()
        ));
    }

    @Operation(
            summary = "온보딩용 리그 목록 조회",
            description = "온보딩 화면에서 선택할 시즌별 리그 목록을 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "리그 목록 조회 성공")
    @GetMapping("/onboarding/leagues")
    public ResponseEntity<List<OnboardingLeagueOptionResponse>> getOnboardingLeagues() {
        return ResponseEntity.ok(ONBOARDING_LEAGUES);
    }

    @Operation(
            summary = "온보딩용 팀 목록 조회",
            description = "온보딩 화면에서 선택할 LCK 팀 목록을 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "팀 목록 조회 성공")
    @GetMapping("/onboarding/teams")
    public ResponseEntity<List<OnboardingTeamOptionResponse>> getOnboardingTeams(
            @Parameter(description = "연도", example = "2026") @RequestParam(defaultValue = "2026") int year) {
        List<OnboardingTeamOptionResponse> teams = findSelectableTeams(year).stream()
                .map(OnboardingTeamOptionResponse::from)
                .toList();
        return ResponseEntity.ok(teams);
    }

    @Operation(
            summary = "온보딩용 선수 목록 조회",
            description = "온보딩 화면에서 선택한 리그/시즌의 선수 목록을 조회합니다. teamId를 전달하면 해당 팀 선수만 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "선수 목록 조회 성공")
    @GetMapping("/onboarding/players")
    public ResponseEntity<List<OnboardingPlayerOptionResponse>> getOnboardingPlayers(
            @Parameter(description = "연도", example = "2026") @RequestParam(defaultValue = "2026") int year,
            @Parameter(description = "팀 ID", example = "1") @RequestParam(required = false) Long teamId) {
        List<OnboardingPlayerOptionResponse> players = playerRepository.findOnboardingPlayers("LCK", year, teamId)
                .stream()
                .map(OnboardingPlayerOptionResponse::from)
                .toList();
        return ResponseEntity.ok(players);
    }

    @Operation(
            summary = "토큰 재발급",
            description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 리프레시 토큰")
    })
    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<TokenResponse> refresh(
            @Parameter(description = "로그인 성공 후 발급된 Refresh Token", required = true,
                    example = "eyJhbGciOiJIUzI1NiJ9.refresh-token")
            @RequestParam String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "유효하지 않은 리프레시 토큰"));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new ResponseStatusException(UNAUTHORIZED, "만료된 리프레시 토큰");
        }

        Member member = stored.getMember();
        String newAccessToken = jwtTokenProvider.createAccessToken(member.getId(), member.isOnboarded());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        refreshTokenRepository.delete(stored);
        refreshTokenRepository.save(RefreshToken.builder()
                .member(member)
                .token(newRefreshToken)
                .expiresAt(jwtTokenProvider.getRefreshTokenExpiry())
                .build());

        return ResponseEntity.ok(new TokenResponse(newAccessToken, newRefreshToken, member.isOnboarded()));
    }

    @Operation(
            summary = "로그아웃",
            description = "현재 로그인 사용자의 Refresh Token을 폐기합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long memberId) {
        if (memberId != null) {
            refreshTokenRepository.deleteByMemberId(memberId);
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "온보딩 완료",
            description = "로그인한 사용자의 선호 리그, 팀, 선수를 저장하고 온보딩을 완료합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "온보딩 완료"),
            @ApiResponse(responseCode = "404", description = "회원 또는 팀을 찾을 수 없음")
    })
    @PostMapping("/onboarding")
    @Transactional
    public ResponseEntity<MemberResponse> onboarding(@AuthenticationPrincipal Long memberId,
                                                      @Valid @RequestBody OnboardingRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "회원을 찾을 수 없습니다"));
        Team team = teamRepository.findById(request.favoriteTeamId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "팀을 찾을 수 없습니다"));
        validateSelectableTeam(team.getId());
        List<Player> favoritePlayers = resolveFavoritePlayers(request.favoritePlayerIds(), team.getId());

        member.completeOnboarding("LCK", team, favoritePlayers);
        return ResponseEntity.ok(MemberResponse.from(member));
    }

    @Operation(
            summary = "내 정보 조회",
            description = "현재 로그인한 사용자의 기본 정보를 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내 정보 조회 성공"),
            @ApiResponse(responseCode = "404", description = "회원을 찾을 수 없음")
    })
    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ResponseEntity<MemberResponse> me(@AuthenticationPrincipal Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "회원을 찾을 수 없습니다"));
        return ResponseEntity.ok(MemberResponse.from(member));
    }

    private List<Team> findSelectableTeams(int year) {
        return findDefaultLckTeams();
    }

    private List<Team> findDefaultLckTeams() {
        Map<String, Team> teamsByCode = teamRepository.findAllByCodeIn(LCK_ONBOARDING_TEAM_CODES).stream()
                .collect(Collectors.toMap(Team::getCode, Function.identity()));

        return LCK_ONBOARDING_TEAM_CODES.stream()
                .map(teamsByCode::get)
                .filter(team -> team != null)
                .toList();
    }

    private void validateSelectableTeam(Long teamId) {
        boolean isSelectableTeam = findSelectableTeams(DEFAULT_ONBOARDING_YEAR).stream()
                .anyMatch(team -> team.getId().equals(teamId));
        if (!isSelectableTeam) {
            throw new ResponseStatusException(BAD_REQUEST, "LCK에 속하지 않는 팀입니다");
        }
    }

    private List<Player> resolveFavoritePlayers(List<Long> favoritePlayerIds, Long teamId) {
        if (favoritePlayerIds == null || favoritePlayerIds.isEmpty()) {
            return List.of();
        }

        Set<Long> requestedIds = new HashSet<>(favoritePlayerIds);
        List<Player> players = playerRepository.findAllById(requestedIds);
        if (players.size() != requestedIds.size()) {
            throw new ResponseStatusException(NOT_FOUND, "선수를 찾을 수 없습니다");
        }

        Set<Long> selectablePlayerIds = playerRepository.findOnboardingPlayers("LCK", DEFAULT_ONBOARDING_YEAR, teamId).stream()
                .map(Player::getId)
                .collect(Collectors.toSet());
        boolean hasUnavailablePlayer = requestedIds.stream()
                .anyMatch(playerId -> !selectablePlayerIds.contains(playerId));
        if (hasUnavailablePlayer) {
            throw new ResponseStatusException(BAD_REQUEST, "선택한 리그/팀에 속하지 않는 선수가 포함되어 있습니다");
        }

        return players;
    }
}
