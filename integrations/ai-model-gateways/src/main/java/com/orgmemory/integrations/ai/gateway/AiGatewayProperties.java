package com.orgmemory.integrations.ai.gateway;

import com.orgmemory.core.ai.AiGatewayCapability;
import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.ai")
public record AiGatewayProperties(
        Map<String, Gateway> gateways,
        Routes routes,
        Set<String> allowedCustomOrigins) {

    public AiGatewayProperties {
        gateways = gateways == null ? Map.of() : Map.copyOf(gateways);
        routes = routes == null ? Routes.defaults() : routes;
        allowedCustomOrigins = allowedCustomOrigins == null
                ? Set.of()
                : allowedCustomOrigins.stream()
                        .map(AiGatewayProperties::normalizeOrigin)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public AiRoute route(AiWorkload workload) {
        Route route = routes.forWorkload(workload);
        return new AiRoute(route.gatewayId(), route.modelId());
    }

    public record Gateway(
            String displayName,
            String baseUrl,
            String apiKey,
            Set<AiGatewayCapability> capabilities,
            Duration timeout) {

        public Gateway {
            displayName = normalize(displayName, "OpenAI-compatible");
            baseUrl = normalizeBaseUrl(baseUrl);
            apiKey = apiKey == null ? "" : apiKey.strip();
            capabilities = capabilities == null || capabilities.isEmpty()
                    ? Set.of(AiGatewayCapability.CHAT)
                    : Set.copyOf(capabilities);
            timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
            if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
                throw new IllegalArgumentException("AI gateway timeout must be between 1 second and 5 minutes");
            }
        }

        boolean configured() {
            return !baseUrl.isBlank() && !apiKey.isBlank();
        }

        @Override
        public String toString() {
            return "Gateway[displayName=%s, baseUrl=%s, apiKey=%s, capabilities=%s, timeout=%s]"
                    .formatted(displayName, baseUrl, apiKey.isBlank() ? "" : "***", capabilities, timeout);
        }

        private static String normalizeBaseUrl(String value) {
            String normalized = value == null ? "" : value.strip().replaceAll("/+$", "");
            if (normalized.isBlank()) {
                return normalized;
            }
            URI uri;
            try {
                uri = URI.create(normalized);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("AI gateway base URL is invalid", exception);
            }
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException(
                        "AI gateway base URL must be an HTTP(S) origin/path without credentials, query, or fragment");
            }
            return normalized;
        }
    }

    public record Route(String gatewayId, String modelId) {

        public Route {
            gatewayId = normalize(gatewayId, "openai");
            modelId = normalize(modelId, "");
        }
    }

    public record Routes(
            Route assistantChat,
            Route keywordPlanning,
            Route graphExtraction,
            Route embedding) {

        public Routes {
            assistantChat = assistantChat == null
                    ? new Route("openai", "gpt-5.6-sol") : assistantChat;
            keywordPlanning = keywordPlanning == null
                    ? assistantChat : keywordPlanning;
            graphExtraction = graphExtraction == null
                    ? new Route("openai", "gpt-5.6-sol") : graphExtraction;
            embedding = embedding == null
                    ? new Route("openai", "text-embedding-3-large") : embedding;
        }

        public Routes(
                Route assistantChat,
                Route graphExtraction,
                Route embedding) {
            this(
                    assistantChat,
                    null,
                    graphExtraction,
                    embedding);
        }

        static Routes defaults() {
            return new Routes(
                    new Route("openai", "gpt-5.6-sol"),
                    null,
                    new Route("openai", "gpt-5.6-sol"),
                    new Route("openai", "text-embedding-3-large"));
        }

        Route forWorkload(AiWorkload workload) {
            return switch (workload) {
                case ASSISTANT_CHAT, PROMPT_EXECUTION -> assistantChat;
                case KEYWORD_PLANNING -> keywordPlanning;
                case GRAPH_EXTRACTION -> graphExtraction;
                case QUERY_EMBEDDING, DOCUMENT_EMBEDDING -> embedding;
            };
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String normalizeOrigin(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "AI gateway allowed origin must not be blank");
        }
        URI uri = URI.create(value.strip());
        if (!("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPath() != null
                        && !uri.getPath().isBlank()
                        && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException(
                    "AI gateway allowed origins must be HTTP(S) origins without a path");
        }
        int port = uri.getPort();
        String authority = uri.getHost().toLowerCase(java.util.Locale.ROOT)
                + (port < 0 ? "" : ":" + port);
        return uri.getScheme().toLowerCase(java.util.Locale.ROOT)
                + "://"
                + authority;
    }
}
