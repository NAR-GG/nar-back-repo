package com.toy.nar.domain.search.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Elasticsearch 검색 문서
 * Team, Player 등의 검색을 위한 인덱스 문서
 */
@Document(indexName = "search", createIndex = false)
@Setting(settingPath = "elasticsearch/settings.json")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchDocument {

    @Id
    private String id; // "TEAM_5", "PLAYER_123"

    @Field(type = FieldType.Keyword)
    private String entityType; // "TEAM", "PLAYER"

    @Field(type = FieldType.Long)
    private Long entityId;

    @Field(type = FieldType.Text, analyzer = "nori_analyzer")
    private String name; // "Gen.G", "Faker"

    @Field(type = FieldType.Text, analyzer = "nori_analyzer")
    private String nameKorean; // "젠지", null

    @Field(type = FieldType.Keyword)
    private String nameNormalized; // "geng", "faker"

    @Field(type = FieldType.Text, analyzer = "edge_ngram_analyzer")
    private String autocomplete; // 자동완성용

    @Field(type = FieldType.Text, analyzer = "nori_analyzer")
    private String aliases; // "젠지, 겐지, GenG"

    @Field(type = FieldType.Keyword)
    private String teamCode; // "T1", "GEN", "C9"

    @Field(type = FieldType.Keyword)
    private String teamImageUrl;

    public static SearchDocument ofTeam(Long teamId, String name, String nameKorean) {
        String normalized = normalize(name);
        return SearchDocument.builder()
                .id("TEAM_" + teamId)
                .entityType("TEAM")
                .entityId(teamId)
                .name(name)
                .nameKorean(nameKorean)
                .nameNormalized(normalized)
                .autocomplete(name + " " + (nameKorean != null ? nameKorean : ""))
                .build();
    }

    public static SearchDocument ofPlayer(Long playerId, String name) {
        String normalized = normalize(name);
        return SearchDocument.builder()
                .id("PLAYER_" + playerId)
                .entityType("PLAYER")
                .entityId(playerId)
                .name(name)
                .nameNormalized(normalized)
                .autocomplete(name)
                .build();
    }

    private static String normalize(String text) {
        if (text == null)
            return "";
        return text.toLowerCase()
                .replaceAll("[^a-z0-9가-힣]", "");
    }
}
