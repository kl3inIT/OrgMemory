package com.orgmemory.mcp;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class AssetDeliveryApiClient {

    private static final ParameterizedTypeReference<List<AssetSummary>>
            ASSET_SUMMARIES = new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    AssetDeliveryApiClient(
            @Qualifier("orgMemoryApiRestClientBuilder")
                    RestClient.Builder restClientBuilder,
            McpGatewayProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.apiBaseUrl().toString())
                .build();
    }

    List<AssetSummary> search(
            String authorization, String query, String type) {
        return request(() -> {
            List<AssetSummary> response = restClient.get()
                    .uri(builder -> builder
                            .path("/api/asset-delivery")
                            .queryParamIfPresent(
                                    "q", Optional.ofNullable(blankToNull(query)))
                            .queryParamIfPresent(
                                    "type", Optional.ofNullable(blankToNull(type)))
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(ASSET_SUMMARIES);
            return response == null ? List.of() : List.copyOf(response);
        });
    }

    AssetRelease getAsset(String authorization, UUID assetId) {
        return get(
                authorization,
                "/api/asset-delivery/{assetId}",
                AssetRelease.class,
                assetId);
    }

    AssetRelease getRelease(
            String authorization, UUID assetId, UUID releaseId) {
        return get(
                authorization,
                "/api/asset-delivery/{assetId}/releases/{releaseId}",
                AssetRelease.class,
                assetId,
                releaseId);
    }

    PackDefinition getPack(
            String authorization, UUID assetId, UUID releaseId) {
        return get(
                authorization,
                "/api/asset-delivery/{assetId}/releases/{releaseId}/pack",
                PackDefinition.class,
                assetId,
                releaseId);
    }

    RelationResolution getRelations(
            String authorization, UUID assetId, UUID releaseId) {
        return get(
                authorization,
                "/api/asset-delivery/{assetId}/releases/{releaseId}/relations",
                RelationResolution.class,
                assetId,
                releaseId);
    }

    PromptRender renderPrompt(
            String authorization,
            UUID assetId,
            UUID releaseId,
            Map<String, Object> variables) {
        return request(() -> restClient.post()
                .uri(
                        "/api/asset-delivery/{assetId}/releases/{releaseId}/prompt-render",
                        assetId,
                        releaseId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .body(Map.of(
                        "variables",
                        variables == null ? Map.of() : Map.copyOf(variables)))
                .retrieve()
                .body(PromptRender.class));
    }

    private <T> T get(
            String authorization,
            String path,
            Class<T> responseType,
            Object... uriVariables) {
        return request(() -> restClient.get()
                .uri(path, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .body(responseType));
    }

    private static <T> T request(ApiCall<T> call) {
        try {
            T response = call.execute();
            if (response == null) {
                throw new AssetDeliveryGatewayException(
                        "OrgMemory returned an empty Asset response");
            }
            return response;
        } catch (RestClientResponseException refused) {
            int status = refused.getStatusCode().value();
            if (status == 400) {
                throw new AssetDeliveryGatewayException(
                        "The Asset request is invalid");
            }
            if (status == 401 || status == 403 || status == 404) {
                throw new AssetDeliveryGatewayException(
                        "The requested Asset is not available to the current identity");
            }
            throw new AssetDeliveryGatewayException(
                    "OrgMemory Asset delivery is temporarily unavailable");
        } catch (RestClientException unavailable) {
            throw new AssetDeliveryGatewayException(
                    "OrgMemory Asset delivery is temporarily unavailable");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    @FunctionalInterface
    private interface ApiCall<T> {

        T execute();
    }

    record AssetSummary(
            UUID assetId,
            String type,
            String namespace,
            String slug,
            String title,
            String summary,
            UUID knowledgeSpaceId,
            String portfolioState,
            UUID releaseId,
            String versionLabel,
            String releaseDigest,
            String availability) {
    }

    record AssetRelease(
            UUID assetId,
            UUID releaseId,
            String type,
            String namespace,
            String slug,
            String versionLabel,
            String title,
            String summary,
            String classification,
            String schemaVersion,
            String payload,
            String digest,
            String availability,
            Instant releasedAt) {
    }

    record PackDefinition(
            UUID packAssetId,
            UUID packReleaseId,
            String releaseDigest,
            String title,
            String versionLabel,
            String purpose,
            String audience,
            List<String> prerequisites,
            String expectedOutcome,
            List<String> completionCriteria,
            String reviewDate,
            String owner,
            boolean accessGap,
            List<PackItem> items) {

        PackDefinition {
            prerequisites = List.copyOf(prerequisites);
            completionCriteria = List.copyOf(completionCriteria);
            items = List.copyOf(items);
        }
    }

    record PackItem(
            String key,
            boolean required,
            int order,
            String kind,
            UUID resourceId,
            UUID pinnedVersionId,
            String title,
            String versionLabel,
            String availability) {
    }

    record RelationResolution(
            UUID assetId,
            UUID releaseId,
            boolean accessGap,
            List<Relation> relations) {

        RelationResolution {
            relations = List.copyOf(relations);
        }
    }

    record Relation(
            String key,
            String kind,
            UUID resourceId,
            UUID pinnedVersionId,
            String title,
            String versionLabel,
            boolean required) {
    }

    record PromptRender(
            UUID assetId,
            UUID releaseId,
            String releaseDigest,
            String systemInstruction,
            String userPrompt,
            List<String> sensitiveVariables,
            String inputShapeDigest) {

        PromptRender {
            sensitiveVariables = List.copyOf(sensitiveVariables);
        }
    }

    static final class AssetDeliveryGatewayException
            extends RuntimeException {

        AssetDeliveryGatewayException(String message) {
            super(message);
        }
    }
}
