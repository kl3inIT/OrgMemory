package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult.CompleteCompletion;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.stereotype.Component;

/**
 * Completes the published Prompt argument and Asset resource-template values.
 *
 * <p>Every suggestion is derived from one authorized Asset delivery call for the
 * current identity, so completion never reveals an Asset the caller could not
 * already read. A delivery failure yields no suggestion rather than an error,
 * because a completion response must not become a second existence channel.
 */
@Component
class AssetCompletionAdapter {

    static final int MAX_VALUES = 20;

    private static final String PROMPT_TEMPLATE_TYPE = "PROMPT_TEMPLATE";
    private static final CompleteResult NONE = new CompleteResult(
            new CompleteCompletion(List.of(), 0, false));
    private static final Logger log =
            LoggerFactory.getLogger(AssetCompletionAdapter.class);

    private final AssetDeliveryApiClient assets;
    private final McpApiAuthorization authorization;

    AssetCompletionAdapter(
            AssetDeliveryApiClient assets,
            McpApiAuthorization authorization) {
        this.assets = assets;
        this.authorization = authorization;
    }

    @McpComplete(prompt = ReleasedPromptAdapter.PROMPT_NAME)
    CompleteResult completeReleasedPromptArgument(
            CompleteRequest request, McpTransportContext context) {
        return switch (request.argument().name()) {
            case ReleasedPromptAdapter.ASSET_ID_ARGUMENT -> assetIds(
                    context,
                    PROMPT_TEMPLATE_TYPE,
                    request.argument().value());
            case ReleasedPromptAdapter.RELEASE_ID_ARGUMENT -> releaseIds(
                    context,
                    PROMPT_TEMPLATE_TYPE,
                    request.argument().value(),
                    resolved(
                            request,
                            ReleasedPromptAdapter.ASSET_ID_ARGUMENT));
            default -> NONE;
        };
    }

    @McpComplete(uri = AssetDeliveryResources.ASSET_URI)
    CompleteResult completeAssetResourceUri(
            CompleteRequest request, McpTransportContext context) {
        if (!AssetDeliveryResources.ASSET_ID_VARIABLE.equals(
                request.argument().name())) {
            return NONE;
        }
        return assetIds(context, null, request.argument().value());
    }

    @McpComplete(uri = AssetDeliveryResources.ASSET_RELEASE_URI)
    CompleteResult completeAssetReleaseResourceUri(
            CompleteRequest request, McpTransportContext context) {
        return switch (request.argument().name()) {
            case AssetDeliveryResources.ASSET_ID_VARIABLE -> assetIds(
                    context, null, request.argument().value());
            case AssetDeliveryResources.RELEASE_ID_VARIABLE -> releaseIds(
                    context,
                    null,
                    request.argument().value(),
                    resolved(
                            request,
                            AssetDeliveryResources.ASSET_ID_VARIABLE));
            default -> NONE;
        };
    }

    private CompleteResult assetIds(
            McpTransportContext context, String type, String typed) {
        return complete(
                context,
                type,
                typed,
                null,
                summary -> summary.assetId().toString());
    }

    private CompleteResult releaseIds(
            McpTransportContext context,
            String type,
            String typed,
            String scopedAssetId) {
        return complete(
                context,
                type,
                typed,
                scopedAssetId,
                summary -> summary.releaseId().toString());
    }

    private CompleteResult complete(
            McpTransportContext context,
            String type,
            String typed,
            String scopedAssetId,
            Function<AssetDeliveryApiClient.AssetSummary, String> value) {
        String prefix = typed == null ? "" : typed.strip();
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (AssetDeliveryApiClient.AssetSummary summary
                : authorized(context, type)) {
            if (scopedAssetId != null
                    && !summary.assetId().toString()
                            .equalsIgnoreCase(scopedAssetId)) {
                continue;
            }
            String candidate = value.apply(summary);
            if (candidate != null && matches(summary, candidate, prefix)) {
                matched.add(candidate);
            }
        }
        return page(List.copyOf(matched));
    }

    private List<AssetDeliveryApiClient.AssetSummary> authorized(
            McpTransportContext context, String type) {
        try {
            return assets.search(
                    authorization.require(context), null, type);
        } catch (McpGatewayException unavailable) {
            log.debug(
                    "MCP completion returned no value: {}",
                    unavailable.getMessage());
            return List.of();
        }
    }

    private static boolean matches(
            AssetDeliveryApiClient.AssetSummary summary,
            String candidate,
            String prefix) {
        if (prefix.isEmpty()) {
            return true;
        }
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return candidate.toLowerCase(Locale.ROOT).startsWith(lowered)
                || contains(summary.title(), lowered)
                || contains(summary.namespace(), lowered)
                || contains(summary.slug(), lowered)
                || contains(summary.versionLabel(), lowered);
    }

    private static boolean contains(String value, String loweredPrefix) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(loweredPrefix);
    }

    private static CompleteResult page(List<String> matched) {
        if (matched.size() <= MAX_VALUES) {
            return new CompleteResult(new CompleteCompletion(
                    matched, matched.size(), false));
        }
        return new CompleteResult(new CompleteCompletion(
                List.copyOf(matched.subList(0, MAX_VALUES)),
                matched.size(),
                true));
    }

    private static String resolved(CompleteRequest request, String name) {
        CompleteRequest.CompleteContext completeContext = request.context();
        if (completeContext == null) {
            return null;
        }
        Map<String, String> arguments = completeContext.arguments();
        if (arguments == null) {
            return null;
        }
        String value = arguments.get(name);
        return McpValues.optionalText(value);
    }
}
