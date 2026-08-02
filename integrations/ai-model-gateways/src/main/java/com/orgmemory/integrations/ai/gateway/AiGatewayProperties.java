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
        return new AiRoute(
                route.gatewayId(),
                route.modelId(),
                route.openAiReasoningEffort());
    }

    public static final class Gateway {

        private String displayName = "OpenAI-compatible";
        private String baseUrl = "";
        private String apiKey = "";
        private Set<AiGatewayCapability> capabilities = Set.of(AiGatewayCapability.CHAT);
        private Duration timeout = Duration.ofSeconds(60);
        private boolean supportsOpenAiReasoningEffort;

        public Gateway() {}

        public Gateway(
                String displayName,
                String baseUrl,
                String apiKey,
                Set<AiGatewayCapability> capabilities,
                Duration timeout,
                boolean supportsOpenAiReasoningEffort) {
            setDisplayName(displayName);
            setBaseUrl(baseUrl);
            setApiKey(apiKey);
            setCapabilities(capabilities);
            setTimeout(timeout);
            setSupportsOpenAiReasoningEffort(supportsOpenAiReasoningEffort);
        }

        public Gateway(
                String displayName,
                String baseUrl,
                String apiKey,
                Set<AiGatewayCapability> capabilities,
                Duration timeout) {
            this(
                    displayName,
                    baseUrl,
                    apiKey,
                    capabilities,
                    timeout,
                    false);
        }

        public String displayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = normalize(displayName, "OpenAI-compatible");
        }

        public String baseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = normalizeBaseUrl(baseUrl);
        }

        public String apiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey.strip();
        }

        public Set<AiGatewayCapability> capabilities() {
            return capabilities;
        }

        public void setCapabilities(Set<AiGatewayCapability> capabilities) {
            this.capabilities = capabilities == null || capabilities.isEmpty()
                    ? Set.of(AiGatewayCapability.CHAT)
                    : Set.copyOf(capabilities);
        }

        public Duration timeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            Duration normalized = timeout == null ? Duration.ofSeconds(60) : timeout;
            if (normalized.isNegative()
                    || normalized.isZero()
                    || normalized.compareTo(Duration.ofMinutes(5)) > 0) {
                throw new IllegalArgumentException(
                        "AI gateway timeout must be between 1 second and 5 minutes");
            }
            this.timeout = normalized;
        }

        public boolean supportsOpenAiReasoningEffort() {
            return supportsOpenAiReasoningEffort;
        }

        public void setSupportsOpenAiReasoningEffort(boolean supportsOpenAiReasoningEffort) {
            this.supportsOpenAiReasoningEffort = supportsOpenAiReasoningEffort;
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

    public record Route(
            String gatewayId,
            String modelId,
            com.orgmemory.core.ai.OpenAiReasoningEffort openAiReasoningEffort) {

        public Route(String gatewayId, String modelId) {
            this(gatewayId, modelId, null);
        }

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
                    ? new Route("openai", "gpt-5.4-mini") : graphExtraction;
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
            return new Routes(null, null, null, null);
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
