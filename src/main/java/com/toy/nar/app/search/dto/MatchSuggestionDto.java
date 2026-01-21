package com.toy.nar.app.search.dto;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record MatchSuggestionDto(
        Long gameId,
        String blueTeamName,
        String redTeamName,
        Boolean blueWin,
        String leagueName,
        LocalDateTime gameDate,
        String label) {
    public static MatchSuggestionDto of(Long gameId, String blueTeamName, String redTeamName,
            Boolean blueWin, String leagueName, LocalDateTime gameDate) {
        String result = blueWin ? blueTeamName + " WIN" : redTeamName + " WIN";
        String label = blueTeamName + " vs " + redTeamName + " (" + result + ")";
        return new MatchSuggestionDto(gameId, blueTeamName, redTeamName, blueWin, leagueName, gameDate, label);
    }
}
