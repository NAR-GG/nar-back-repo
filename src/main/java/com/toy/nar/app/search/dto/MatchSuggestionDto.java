package com.toy.nar.app.search.dto;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record MatchSuggestionDto(
                Long gameId,
                String blueTeamName,
                String blueTeamCode,
                String blueTeamImageUrl,
                int blueTeamScore,
                String redTeamName,
                String redTeamCode,
                String redTeamImageUrl,
                int redTeamScore,
                Boolean blueWin,
                String leagueName,
                LocalDateTime gameDate,
                String patch) {

        public static MatchSuggestionDto of(Long gameId,
                        String blueTeamName, String blueTeamCode, String blueTeamImageUrl, int blueTeamScore,
                        String redTeamName, String redTeamCode, String redTeamImageUrl, int redTeamScore,
                        Boolean blueWin, String leagueName, LocalDateTime gameDate,
                        String patch) {

                return new MatchSuggestionDto(
                                gameId,
                                blueTeamName, blueTeamCode, blueTeamImageUrl, blueTeamScore,
                                redTeamName, redTeamCode, redTeamImageUrl, redTeamScore,
                                blueWin,
                                leagueName,
                                gameDate,
                                patch);
        }
}
