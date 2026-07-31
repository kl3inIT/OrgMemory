package com.orgmemory.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
        return GatewayRequests.request(() -> {
            List<AssetSummary> response = restClient.get()
                    .uri(builder -> builder
                            .path("/api/asset-delivery")
                            .queryParamIfPresent(
                                    "q", Optional.ofNullable(McpValues.optionalText(query)))
                            .queryParamIfPresent(
                                    "type", Optional.ofNullable(McpValues.optionalText(type)))
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(ASSET_SUMMARIES);
            return response == null ? List.of() : List.copyOf(response);
        }, GatewayRequests.ASSET);
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

    SkillManifest getSkillManifest(
            String authorization, UUID assetId, UUID releaseId) {
        return get(
                authorization,
                "/api/asset-delivery/{assetId}/releases/{releaseId}/skill-manifest",
                SkillManifest.class,
                assetId,
                releaseId);
    }

    SkillManifest resolveSkillManifest(
            String authorization,
            String namespace,
            String slug,
            String version) {
        return get(
                authorization,
                "/api/asset-delivery/skills/{namespace}/{slug}/versions/{version}/manifest",
                SkillManifest.class,
                namespace,
                slug,
                version);
    }

    void copySkillPackage(
            String authorization,
            UUID assetId,
            UUID releaseId,
            OutputStream output) {
        GatewayRequests.request(() -> restClient.get()
                .uri(
                        "/api/asset-delivery/{assetId}/releases/{releaseId}/skill-package",
                        assetId,
                        releaseId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new McpGatewayException(
                                "The requested Skill package is not available to the current identity");
                    }
                    try {
                        response.getBody().transferTo(output);
                    } catch (IOException unavailable) {
                        throw new McpGatewayException(
                                "OrgMemory Skill package delivery was interrupted",
                                unavailable);
                    }
                    return Boolean.TRUE;
                }), GatewayRequests.ASSET);
    }

    PromptRender renderPrompt(
            String authorization,
            UUID assetId,
            UUID releaseId,
            Map<String, Object> variables) {
        return GatewayRequests.request(() -> restClient.post()
                .uri(
                        "/api/asset-delivery/{assetId}/releases/{releaseId}/prompt-render",
                        assetId,
                        releaseId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .body(Map.of(
                        "variables",
                        copyVariables(variables)))
                .retrieve()
                .body(PromptRender.class), GatewayRequests.ASSET);
    }

    private <T> T get(
            String authorization,
            String path,
            Class<T> responseType,
            Object... uriVariables) {
        return GatewayRequests.request(() -> restClient.get()
                .uri(path, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .body(responseType), GatewayRequests.ASSET);
    }

    private static Map<String, Object> copyVariables(
            Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(variables));
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

    record SkillManifest(
            UUID assetId,
            UUID releaseId,
            String namespace,
            String slug,
            String coordinate,
            String version,
            String title,
            String description,
            String releaseDigest,
            String packageDigest,
            long packageLength,
            String mediaType,
            String license,
            String compatibility,
            String allowedTools,
            Map<String, String> metadata,
            List<SkillFile> files) {

        SkillManifest {
            metadata = Map.copyOf(metadata);
            files = List.copyOf(files);
        }
    }

    record SkillFile(String path, long size, String sha256) {
    }

}
