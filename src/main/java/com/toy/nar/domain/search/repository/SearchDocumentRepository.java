package com.toy.nar.domain.search.repository;

import java.util.List;

import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.toy.nar.domain.search.document.SearchDocument;

public interface SearchDocumentRepository extends ElasticsearchRepository<SearchDocument, String> {

    /**
     * 이름으로 검색 (Nori 분석기 적용)
     */
    List<SearchDocument> findByName(String name);

    /**
     * 엔티티 타입으로 필터링하여 검색
     */
    List<SearchDocument> findByEntityTypeAndNameContaining(String entityType, String name);

    /**
     * 통합 검색: 이름, 한글이름, 별칭, 자동완성 필드에서 검색
     */
    @Query("""
            {
              "bool": {
                "should": [
                  { "match": { "name": { "query": "?0", "boost": 3.0 } } },
                  { "match": { "nameKorean": { "query": "?0", "boost": 2.0 } } },
                  { "match": { "aliases": { "query": "?0", "boost": 1.5 } } },
                  { "match": { "autocomplete": { "query": "?0", "boost": 1.0 } } },
                  { "prefix": { "nameNormalized": { "value": "?0", "boost": 2.0 } } }
                ],
                "minimum_should_match": 1
              }
            }
            """)
    List<SearchDocument> searchByKeyword(String keyword);

    /**
     * 엔티티 타입 + 키워드 검색
     */
    @Query("""
            {
              "bool": {
                "must": [
                  { "term": { "entityType": "?0" } }
                ],
                "should": [
                  { "match": { "name": { "query": "?1", "boost": 3.0 } } },
                  { "match": { "nameKorean": { "query": "?1", "boost": 2.0 } } },
                  { "match": { "autocomplete": { "query": "?1", "boost": 1.0 } } },
                  { "prefix": { "nameNormalized": { "value": "?1", "boost": 2.0 } } }
                ],
                "minimum_should_match": 1
              }
            }
            """)
    List<SearchDocument> searchByTypeAndKeyword(String entityType, String keyword);
}
