package com.toy.nar.app.lolesports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WorldsServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private WorldsService worldsService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        worldsService = new WorldsService(webClient);
    }

    @Test
    @DisplayName("inProgress 상태이고 스트림 정보가 없을 때 기본 URL(LCK) 반환")
    void fetchMatchDetailsAsync_inProgress_noStreams() throws Exception {
        // given
        String eventId = "123";
        String matchDate = "2026-01-21T17:00:00Z";
        String stageName = "Regular Season";
        String matchState = "inProgress";

        // LCK, streams 없음
        JsonNode mockResponse = createMockDetailResponse("LCK", "inProgress", null);
        setupWebClientMock(mockResponse);

        // when
        // private method 호출을 위해 Reflection 사용
        Mono<MatchResultDto> resultMono = (Mono<MatchResultDto>) ReflectionTestUtils.invokeMethod(
                worldsService,
                "fetchMatchDetailsAsync",
                eventId, matchDate, stageName, matchState
        );

        MatchResultDto result = resultMono.block();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getState()).isEqualTo("inProgress");
        // LCK 기본 URL 확인
        assertThat(result.getLiveStreamUrl()).isEqualTo("https://play.sooplive.co.kr/aflol");
    }

    @Test
    @DisplayName("inProgress 상태이고 스트림 정보가 없을 때 기본 URL(LPL) 반환")
    void fetchMatchDetailsAsync_inProgress_noStreams_LPL() throws Exception {
        // given
        String eventId = "123";
        String matchDate = "2026-01-21T17:00:00Z";
        String stageName = "Regular Season";
        String matchState = "inProgress";

        // LPL, streams 없음
        JsonNode mockResponse = createMockDetailResponse("LPL", "inProgress", null);
        setupWebClientMock(mockResponse);

        // when
        Mono<MatchResultDto> resultMono = (Mono<MatchResultDto>) ReflectionTestUtils.invokeMethod(
                worldsService,
                "fetchMatchDetailsAsync",
                eventId, matchDate, stageName, matchState
        );

        MatchResultDto result = resultMono.block();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getState()).isEqualTo("inProgress");
        // LPL 기본 URL 확인
        assertThat(result.getLiveStreamUrl()).isEqualTo("https://www.twitch.tv/lpl");
    }

    @Test
    @DisplayName("inProgress 상태이고 한국어 유튜브 스트림이 있을 때 해당 URL 반환 (우선순위 1)")
    void fetchMatchDetailsAsync_inProgress_koYoutube() throws Exception {
        // given
        String eventId = "124";
        String matchDate = "2026-01-21T17:00:00Z";
        String stageName = "Regular Season";
        String matchState = "inProgress";

        JsonNode streams = createStreamNode("ko-KR", "youtube", "video123");
        setupWebClientMock(createMockDetailResponse("LCK", "inProgress", streams));

        // when
        Mono<MatchResultDto> resultMono = (Mono<MatchResultDto>) ReflectionTestUtils.invokeMethod(
                worldsService,
                "fetchMatchDetailsAsync",
                eventId, matchDate, stageName, matchState
        );

        MatchResultDto result = resultMono.block();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getLiveStreamUrl()).isEqualTo("https://www.youtube.com/watch?v=video123");
    }

    @Test
    @DisplayName("inProgress 상태이고 영어 트위치 스트림만 있을 때 해당 URL 반환 (우선순위 2)")
    void fetchMatchDetailsAsync_inProgress_enTwitch() throws Exception {
        // given
        String eventId = "125";
        String matchDate = "2026-01-21T17:00:00Z";
        String stageName = "Regular Season";
        String matchState = "inProgress";

        JsonNode streams = createStreamNode("en-US", "twitch", "lck");
        setupWebClientMock(createMockDetailResponse("LCK", "inProgress", streams));

        // when
        Mono<MatchResultDto> resultMono = (Mono<MatchResultDto>) ReflectionTestUtils.invokeMethod(
                worldsService,
                "fetchMatchDetailsAsync",
                eventId, matchDate, stageName, matchState
        );

        MatchResultDto result = resultMono.block();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getLiveStreamUrl()).isEqualTo("https://www.twitch.tv/lck");
    }

    private void setupWebClientMock(JsonNode responseBody) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseBody));
    }

    private JsonNode createMockDetailResponse(String leagueSlug, String state, JsonNode streams) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode data = root.putObject("data");
        ObjectNode event = data.putObject("event");
        
        event.put("state", state);
        event.putObject("league").put("slug", leagueSlug);

        if (streams != null) {
            event.set("streams", streams);
        } else {
            event.putArray("streams");
        }

        ObjectNode match = event.putObject("match");
        ArrayNode teams = match.putArray("teams");
        teams.addObject().put("code", "T1").put("name", "T1").putObject("result").put("gameWins", 1);
        teams.addObject().put("code", "GEN").put("name", "Gen.G").putObject("result").put("gameWins", 0);
        
        match.putArray("games"); // games empty
        
        return root;
    }

    private JsonNode createStreamNode(String locale, String provider, String parameter) {
        ArrayNode streams = objectMapper.createArrayNode();
        streams.addObject()
                .put("locale", locale)
                .put("provider", provider)
                .put("parameter", parameter);
        return streams;
    }
}
