package com.toy.nar.app.search.dto;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record MatchSuggestionDto(
        Long gameId,
        String blueTeamName,
        String blueTeamCode,
        String blueTeamImageUrl,
        String redTeamName,
        String redTeamCode,
        String redTeamImageUrl,
        Boolean blueWin,
        String leagueName,
        LocalDateTime gameDate,
        String patch,
        Integer gameNumber,
        String label) {
    public static MatchSuggestionDto of(Long gameId,
            String blueTeamName, String blueTeamCode, String blueTeamImageUrl,
            String redTeamName, String redTeamCode, String redTeamImageUrl,
            Boolean blueWin, String leagueName, LocalDateTime gameDate,
            String patch, Integer gameNumber) {

        String result = blueWin ? blueTeamName + " WIN" : redTeamName + " WIN";
        String label = blueTeamName + " vs " + redTeamName + " (" + result + ")";

        return new MatchSuggestionDto(
                gameId,
                blueTeamName, blueTeamCode, blueTeamImageUrl,
                redTeamName, redTeamCode, redTeamImageUrl,
                blueWin,
                leagueName,
                gameDate,
                patch,
                gameNumber,
                label);
    }
}
