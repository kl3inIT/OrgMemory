package com.orgmemory.integrations.ai.openai;

import com.orgmemory.core.ai.AiGatewayEndpointPolicy;
import com.orgmemory.core.ai.AiGatewayPreset;
import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
final class ConfiguredAiGatewayEndpointPolicy
        implements AiGatewayEndpointPolicy {

    private static final Map<AiGatewayPreset, String> FIXED_BASE_URLS = Map.of(
            AiGatewayPreset.OPENAI,
            "https://api.openai.com/v1",
            AiGatewayPreset.ANTHROPIC,
            "https://api.anthropic.com",
            AiGatewayPreset.OPENROUTER,
            "https://openrouter.ai/api/v1");

    private final AiGatewayProperties properties;

    ConfiguredAiGatewayEndpointPolicy(AiGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public String requireAllowed(
            AiGatewayPreset preset,
            AiGatewayProtocol protocol,
            String baseUrl) {
        requireCompatible(preset, protocol);
        String fixed = FIXED_BASE_URLS.get(preset);
        if (fixed != null) {
            if (baseUrl != null
                    && !baseUrl.isBlank()
                    && !stripTrailingSlash(baseUrl).equals(fixed)) {
                throw invalid("This provider uses a fixed API endpoint");
            }
            return fixed;
        }
        URI uri;
        try {
            uri = URI.create(stripTrailingSlash(baseUrl));
        } catch (RuntimeException invalid) {
            throw invalid("AI gateway base URL is invalid");
        }
        if (!("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw invalid(
                    "AI gateway base URL must be HTTP(S) without credentials, query, or fragment");
        }
        String origin = origin(uri);
        if (!properties.allowedCustomOrigins().contains(origin)) {
            throw invalid(
                    "This AI gateway origin is not allowed by deployment policy");
        }
        return stripTrailingSlash(uri.toString());
    }

    private static void requireCompatible(
            AiGatewayPreset preset,
            AiGatewayProtocol protocol) {
        boolean compatible = switch (preset) {
            case ANTHROPIC -> protocol == AiGatewayProtocol.ANTHROPIC_MESSAGES;
            case OPENAI,
                    NINE_ROUTER,
                    OPENROUTER,
                    LITELLM,
                    OLLAMA,
                    OPENAI_COMPATIBLE ->
                protocol == AiGatewayProtocol.OPENAI_COMPATIBLE;
        };
        if (!compatible) {
            throw invalid(
                    "The provider preset does not support this protocol");
        }
    }

    private static String origin(URI uri) {
        String authority = uri.getHost().toLowerCase(Locale.ROOT)
                + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        return uri.getScheme().toLowerCase(Locale.ROOT)
                + "://"
                + authority;
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().replaceAll("/+$", "");
    }

    private static BusinessValidationException invalid(String message) {
        return new BusinessValidationException(
                "ai.gateway-endpoint-invalid",
                message);
    }
}
