package com.orgmemory.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
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
            evidence = List.copyOf(evidence);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Evidence(
            UUID citationId,
            UUID knowledgeAssetId,
            String title,
            String content,
            @Nullable String sourceUri,
            @Nullable Integer startPage,
            @Nullable Integer endPage,
            @Nullable String heading,
            double relevanceScore) {
    }

}
