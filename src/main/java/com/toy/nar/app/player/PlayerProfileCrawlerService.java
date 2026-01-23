package com.toy.nar.app.player;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerProfileCrawlerService {

    private static final String BASE_URL = "https://www.trackingthepros.com/player/";
    private static final int TIMEOUT_MS = 10000;

    /**
     * TrackingThePros에서 선수 프로필 정보 크롤링
     * 
     * @param playerName 선수 활동명 (Faker, Peyz, Roamer 등)
     */
    public PlayerProfileDto crawlPlayerProfile(String playerName) {
        try {
            String url = BASE_URL + playerName;
            log.info("Crawling player profile from TrackingThePros: {}", url);

            Document doc = Jsoup.connect(url)
                    .userAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,ko;q=0.8")
                    .timeout(TIMEOUT_MS)
                    .get();

            return parsePlayerProfile(doc, playerName);
        } catch (IOException e) {
            log.error("Failed to crawl TrackingThePros for player: {}", playerName, e);
            return PlayerProfileDto.builder().gameName(playerName).build();
        }
    }

    private PlayerProfileDto parsePlayerProfile(Document doc, String playerName) {
        PlayerProfileDto.PlayerProfileDtoBuilder builder = PlayerProfileDto.builder();
        builder.gameName(playerName);

        // Player Info 섹션에서 데이터 추출
        extractPlayerInfo(doc, builder);

        // Accounts 섹션에서 게임 계정 추출 (티어 포함)
        List<PlayerProfileDto.GameAccountDto> gameAccounts = extractGameAccounts(doc);
        builder.gameAccounts(gameAccounts);

        return builder.build();
    }

    /**
     * Player Info 섹션에서 선수 정보 추출
     */
    private void extractPlayerInfo(Document doc, PlayerProfileDto.PlayerProfileDtoBuilder builder) {
        // "Player Info" 헤더를 찾아서 그 부모 컨테이너에서 정보 추출
        Elements headers = doc.select("h4");
        Element playerInfoHeader = null;

        for (Element header : headers) {
            if (header.text().contains("Player Info")) {
                playerInfoHeader = header;
                break;
            }
        }

        if (playerInfoHeader == null) {
            log.warn("Player Info section not found");
            return;
        }

        Element container = playerInfoHeader.parent();
        if (container == null)
            return;

        // Name 추출: "Kim Su-hwan (김수환)" 형태
        extractName(container, builder);

        // Birthday 추출: "December 5, 2005 (20)" 형태
        extractBirthday(container, builder);

        // Role 추출
        extractField(container, "Role", value -> builder.role(value));
    }

    /**
     * 특정 라벨의 값 추출
     */
    private void extractField(Element container, String label, java.util.function.Consumer<String> setter) {
        Elements allElements = container.getAllElements();
        for (int i = 0; i < allElements.size(); i++) {
            Element el = allElements.get(i);
            if (el.ownText().trim().equals(label)) {
                // 다음 sibling에서 값 찾기
                Element next = el.nextElementSibling();
                if (next != null) {
                    String value = next.text().trim();
                    if (!value.isEmpty()) {
                        setter.accept(value);
                        return;
                    }
                }
            }
        }
    }

    /**
     * 이름 추출: "Name" 라벨 다음의 값에서 본명 파싱
     * 예: "Kim Su-hwan (김수환)" -> realName = "김수환" 또는 영문명
     */
    private void extractName(Element container, PlayerProfileDto.PlayerProfileDtoBuilder builder) {
        extractField(container, "Name", value -> {
            // 괄호 안의 한글 이름 추출
            Pattern koreanPattern = Pattern.compile("\\(([가-힣]+)\\)");
            Matcher matcher = koreanPattern.matcher(value);
            if (matcher.find()) {
                builder.realName(matcher.group(1));
            } else {
                // 한글이 없으면 전체를 본명으로
                builder.realName(value);
            }
        });
    }

    /**
     * 생년월일 추출: "December 5, 2005 (20)" 형태
     */
    private void extractBirthday(Element container, PlayerProfileDto.PlayerProfileDtoBuilder builder) {
        extractField(container, "Birthday", value -> {
            // 나이 추출: (20) 형태
            Pattern agePattern = Pattern.compile("\\((\\d+)\\)");
            Matcher ageMatcher = agePattern.matcher(value);
            if (ageMatcher.find()) {
                builder.age(Integer.parseInt(ageMatcher.group(1)));
            }

            // 날짜 추출: "December 5, 2005" 형태
            Pattern datePattern = Pattern.compile("([A-Za-z]+)\\s+(\\d{1,2}),\\s+(\\d{4})");
            Matcher dateMatcher = datePattern.matcher(value);
            if (dateMatcher.find()) {
                try {
                    String dateStr = dateMatcher.group(1) + " " + dateMatcher.group(2) + ", " + dateMatcher.group(3);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
                    LocalDate date = LocalDate.parse(dateStr, formatter);
                    builder.birthDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
                } catch (Exception e) {
                    log.warn("Failed to parse birthday: {}", value);
                }
            }
        });
    }

    /**
     * Accounts 섹션에서 게임 계정 목록 추출 (티어 정보 포함)
     * 예: "[KR] Peyz#KR11 Challenger 1,200LP" -> GameAccountDto(riotId="Peyz #KR11",
     * tier="Challenger 1,200LP")
     */
    private List<PlayerProfileDto.GameAccountDto> extractGameAccounts(Document doc) {
        List<PlayerProfileDto.GameAccountDto> accounts = new ArrayList<>();

        // "Accounts" 헤더 찾기
        Elements headers = doc.select("h4");
        Element accountsHeader = null;

        for (Element header : headers) {
            if (header.text().contains("Accounts")) {
                accountsHeader = header;
                break;
            }
        }

        if (accountsHeader == null) {
            log.warn("Accounts section not found");
            return accounts;
        }

        Element container = accountsHeader.parent();
        if (container == null)
            return accounts;

        // [REGION] Name#Tag Tier LP 패턴 찾기
        String text = container.text();

        // 패턴: [지역] 닉네임#태그 티어 LP정보
        Pattern pattern = Pattern.compile("\\[([A-Z]+)\\]\\s*([^\\[\\]]+?)(?=\\s*\\[|$)");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String accountInfo = matcher.group(2).trim();

            // Riot ID와 티어 분리
            String riotId = null;
            String tier = null;

            // 티어 키워드를 기준으로 분리 (띄어쓰기 있는 태그 지원: #N S 등)
            Pattern tierPattern = Pattern.compile(
                    "(Challenger|Ch|Grandmaster|GM|Master|Diamond|Emerald|Platinum|Gold|Silver|Bronze|Iron)\\s*\\d*,?\\d*\\s*LP.*");
            Matcher tierMatcher = tierPattern.matcher(accountInfo);

            if (tierMatcher.find()) {
                // 티어 키워드 위치를 기준으로 분리
                int tierStart = tierMatcher.start();
                riotId = accountInfo.substring(0, tierStart).trim();
                tier = tierMatcher.group(0).trim();
            } else {
                // 티어 정보가 없으면 전체가 riotId
                riotId = accountInfo.trim();
            }

            if (riotId != null && !riotId.isEmpty()) {
                accounts.add(PlayerProfileDto.GameAccountDto.builder()
                        .riotId(riotId)
                        .tier(tier)
                        .build());
            }
        }

        return accounts;
    }
}
