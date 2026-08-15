package com.orgmemory.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class KnowledgeSearchApiClient {

    private final RestClient restClient;

    KnowledgeSearchApiClient(
            @Qualifier("orgMemoryApiRestClientBuilder")
                    RestClient.Builder restClientBuilder,
            McpGatewayProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.apiBaseUrl().toString())
                .build();
    }

    SearchResult search(
            String authorization,
            String query,
            Integer limit) {
        return GatewayRequests.request(() -> restClient.get()
                    .uri(builder -> builder
                            .path("/api/knowledge/search")
                            .queryParam("q", query)
                            .queryParamIfPresent(
                                    "limit",
                                    Optional.ofNullable(limit))
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(SearchResult.class), GatewayRequests.KNOWLEDGE_SEARCH);
    }

    record SearchResult(String requestId, List<Evidence> evidence) {

        SearchResult {
            List<Evidence> numbered = new ArrayList<>(evidence.size());
            for (int index = 0; index < evidence.size(); index++) {
                numbered.add(evidence.get(index).withSourceNumber(index + 1));
            }
            evidence = List.copyOf(numbered);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Evidence(
            Integer sourceNumber,
            UUID citationId,
            UUID knowledgeAssetId,
            String title,
            String content,
            @Nullable String sourceUri,
            @Nullable Integer startPage,
            @Nullable Integer endPage,
            @Nullable String heading,
            double relevanceScore) {

        Evidence(
                UUID citationId,
                UUID knowledgeAssetId,
                String title,
                String content,
                @Nullable String sourceUri,
                @Nullable Integer startPage,
                @Nullable Integer endPage,
                @Nullable String heading,
                double relevanceScore) {
            this(
                    0,
                    citationId,
                    knowledgeAssetId,
                    title,
                    content,
                    sourceUri,
                    startPage,
                    endPage,
                    heading,
                    relevanceScore);
        }

        Evidence withSourceNumber(int value) {
            return new Evidence(
                    value,
                    citationId,
                    knowledgeAssetId,
                    title,
                    content,
                    sourceUri,
                    startPage,
                    endPage,
                    heading,
                    relevanceScore);
        }
    }

}
