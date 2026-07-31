package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest.CompleteArgument;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest.CompleteContext;
import io.modelcontextprotocol.spec.McpSchema.CompleteReference;
import io.modelcontextprotocol.spec.McpSchema.PromptReference;
import io.modelcontextprotocol.spec.McpSchema.ResourceReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetCompletionAdapterTests {

    private static final String ONBOARDING_ASSET =
            "70000000-0000-0000-0000-000000000001";
    private static final String ONBOARDING_RELEASE =
            "80000000-0000-0000-0000-000000000001";
    private static final String PAYROLL_ASSET =
            "70000000-0000-0000-0000-000000000002";
    private static final String PAYROLL_RELEASE =
            "80000000-0000-0000-0000-000000000002";

    private final AssetDeliveryApiClient assets =
            mock(AssetDeliveryApiClient.class);
    private final McpApiAuthorization authorization =
            mock(McpApiAuthorization.class);
    private final AssetCompletionAdapter adapter =
            new AssetCompletionAdapter(assets, authorization);
    private final McpTransportContext context =
            McpTransportContext.create(Map.of("request", "test"));

    @Test
    void suggestsOnlyAuthorizedPromptReleasesAndMatchesReadableText() {
        authorizedPromptTemplates(
                summary(ONBOARDING_ASSET, ONBOARDING_RELEASE, "Onboarding brief", "onboarding-brief"),
                summary(PAYROLL_ASSET, PAYROLL_RELEASE, "Payroll close", "payroll-close"));

        var completion = adapter.completeReleasedPromptArgument(
                promptRequest(
                        ReleasedPromptAdapter.ASSET_ID_ARGUMENT,
                        "onboard",
                        null),
                context);

        assertEquals(List.of(ONBOARDING_ASSET), completion.completion().values());
        assertEquals(1, completion.completion().total());
        assertFalse(completion.completion().hasMore());
    }

    @Test
    void matchesAnIdentifierPrefixAsWellAsReadableText() {
        authorizedPromptTemplates(
                summary(ONBOARDING_ASSET, ONBOARDING_RELEASE, "Onboarding brief", "onboarding-brief"),
                summary(PAYROLL_ASSET, PAYROLL_RELEASE, "Payroll close", "payroll-close"));

        var completion = adapter.completeReleasedPromptArgument(
                promptRequest(
                        ReleasedPromptAdapter.ASSET_ID_ARGUMENT,
                        "70000000-0000-0000-0000-000000000002",
                        null),
                context);

        assertEquals(List.of(PAYROLL_ASSET), completion.completion().values());
    }

    @Test
    void scopesReleaseSuggestionsToTheAlreadyResolvedAsset() {
        authorizedPromptTemplates(
                summary(ONBOARDING_ASSET, ONBOARDING_RELEASE, "Onboarding brief", "onboarding-brief"),
                summary(PAYROLL_ASSET, PAYROLL_RELEASE, "Payroll close", "payroll-close"));

        var completion = adapter.completeReleasedPromptArgument(
                promptRequest(
                        ReleasedPromptAdapter.RELEASE_ID_ARGUMENT,
                        "",
                        Map.of(
                                ReleasedPromptAdapter.ASSET_ID_ARGUMENT,
                                PAYROLL_ASSET)),
                context);

        assertEquals(List.of(PAYROLL_RELEASE), completion.completion().values());
    }

    @Test
    void returnsNoSuggestionWhenAssetDeliveryRefusesTheIdentity() {
        when(authorization.require(context))
                .thenReturn("Bearer exchanged-api-token");
        when(assets.search("Bearer exchanged-api-token", null, "PROMPT_TEMPLATE"))
                .thenThrow(new McpGatewayException(
                        "The requested Asset is not available to the current identity"));

        var completion = adapter.completeReleasedPromptArgument(
                promptRequest(
                        ReleasedPromptAdapter.ASSET_ID_ARGUMENT,
                        "onboard",
                        null),
                context);

        assertEquals(List.of(), completion.completion().values());
        assertEquals(0, completion.completion().total());
        assertFalse(completion.completion().hasMore());
    }

    @Test
    void doesNotSuggestValuesForAnUnknownArgument() {
        var completion = adapter.completeReleasedPromptArgument(
                promptRequest("variables_json", "", null), context);

        assertEquals(List.of(), completion.completion().values());
    }

    @Test
    void capsSuggestionsAndReportsThatMoreExist() {
        var many = new ArrayList<AssetDeliveryApiClient.AssetSummary>();
        for (int index = 0; index < AssetCompletionAdapter.MAX_VALUES + 5; index++) {
            many.add(summary(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "Prompt " + index,
                    "prompt-" + index));
        }
        authorizedPromptTemplates(many.toArray(
                AssetDeliveryApiClient.AssetSummary[]::new));

        var completion = adapter.completeReleasedPromptArgument(
                promptRequest(
                        ReleasedPromptAdapter.ASSET_ID_ARGUMENT, "", null),
                context);

        assertEquals(
                AssetCompletionAdapter.MAX_VALUES,
                completion.completion().values().size());
        assertEquals(
                AssetCompletionAdapter.MAX_VALUES + 5,
                completion.completion().total());
        assertTrue(completion.completion().hasMore());
    }

    @Test
    void completesResourceTemplateVariablesAcrossEveryAuthorizedAssetType() {
        when(authorization.require(context))
                .thenReturn("Bearer exchanged-api-token");
        when(assets.search("Bearer exchanged-api-token", null, null))
                .thenReturn(List.of(summary(
                        ONBOARDING_ASSET,
                        ONBOARDING_RELEASE,
                        "Onboarding brief",
                        "onboarding-brief")));

        var assetCompletion = adapter.completeAssetResourceUri(
                resourceRequest(
                        AssetDeliveryResources.ASSET_URI,
                        AssetDeliveryResources.ASSET_ID_VARIABLE,
                        "",
                        null),
                context);
        var releaseCompletion = adapter.completeAssetReleaseResourceUri(
                resourceRequest(
                        AssetDeliveryResources.ASSET_RELEASE_URI,
                        AssetDeliveryResources.RELEASE_ID_VARIABLE,
                        "",
                        Map.of(
                                AssetDeliveryResources.ASSET_ID_VARIABLE,
                                ONBOARDING_ASSET)),
                context);

        assertEquals(
                List.of(ONBOARDING_ASSET),
                assetCompletion.completion().values());
        assertEquals(
                List.of(ONBOARDING_RELEASE),
                releaseCompletion.completion().values());
    }

    private void authorizedPromptTemplates(
            AssetDeliveryApiClient.AssetSummary... summaries) {
        when(authorization.require(context))
                .thenReturn("Bearer exchanged-api-token");
        when(assets.search("Bearer exchanged-api-token", null, "PROMPT_TEMPLATE"))
                .thenReturn(List.of(summaries));
    }

    private static CompleteRequest promptRequest(
            String argument, String value, Map<String, String> resolved) {
        return request(
                new PromptReference(ReleasedPromptAdapter.PROMPT_NAME),
                argument,
                value,
                resolved);
    }

    private static CompleteRequest resourceRequest(
            String uri,
            String argument,
            String value,
            Map<String, String> resolved) {
        return request(
                new ResourceReference(uri), argument, value, resolved);
    }

    private static CompleteRequest request(
            CompleteReference reference,
            String argument,
            String value,
            Map<String, String> resolved) {
        return new CompleteRequest(
                reference,
                new CompleteArgument(argument, value),
                null,
                resolved == null ? null : new CompleteContext(resolved));
    }

    private static AssetDeliveryApiClient.AssetSummary summary(
            String assetId, String releaseId, String title, String slug) {
        return new AssetDeliveryApiClient.AssetSummary(
                UUID.fromString(assetId),
                "PROMPT_TEMPLATE",
                "hr",
                slug,
                title,
                "Summary of " + title,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "ACTIVE",
                UUID.fromString(releaseId),
                "v1",
                "digest",
                "USABLE");
    }
}
