package com.orgmemory.mcp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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
        try {
            SearchResult response = restClient.get()
                    .uri(builder -> builder
                            .path("/api/knowledge/search")
                            .queryParam("q", query)
                            .queryParamIfPresent(
                                    "limit",
                                    Optional.ofNullable(limit))
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(SearchResult.class);
            if (response == null) {
                throw new KnowledgeSearchGatewayException(
                        "OrgMemory returned an empty search response");
            }
            return response;
        } catch (RestClientResponseException refused) {
            if (refused.getStatusCode().value() == 401
                    || refused.getStatusCode().value() == 403
                    || refused.getStatusCode().value() == 404) {
                throw new KnowledgeSearchGatewayException(
                        "The current identity cannot access that knowledge");
            }
            throw new KnowledgeSearchGatewayException(
                    "OrgMemory knowledge search is temporarily unavailable");
        } catch (RestClientException unavailable) {
            throw new KnowledgeSearchGatewayException(
                    "OrgMemory knowledge search is temporarily unavailable");
        }
    }

    record SearchResult(String requestId, List<Evidence> evidence) {

        SearchResult {
            evidence = List.copyOf(evidence);
        }
    }

    record Evidence(
            UUID citationId,
            UUID knowledgeAssetId,
            String title,
            String content,
            String sourceUri,
            Integer startPage,
            Integer endPage,
            String heading,
            double relevanceScore) {
    }

    static final class KnowledgeSearchGatewayException
            extends RuntimeException {

        KnowledgeSearchGatewayException(String message) {
            super(message);
        }
    }
}
