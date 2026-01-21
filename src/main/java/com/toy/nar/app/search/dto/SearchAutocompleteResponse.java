package com.toy.nar.app.search.dto;

import java.util.List;

public record SearchAutocompleteResponse(
        List<MatchSuggestionDto> suggestions) {
    public static SearchAutocompleteResponse of(List<MatchSuggestionDto> suggestions) {
        return new SearchAutocompleteResponse(suggestions);
    }

    public static SearchAutocompleteResponse empty() {
        return new SearchAutocompleteResponse(List.of());
    }
}
