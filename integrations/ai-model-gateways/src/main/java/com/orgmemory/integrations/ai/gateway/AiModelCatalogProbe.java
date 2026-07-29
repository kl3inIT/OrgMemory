package com.orgmemory.integrations.ai.gateway;

import com.orgmemory.core.ai.AiGatewayConnection;
import com.orgmemory.core.ai.AiGatewayEndpointPolicy;
import com.orgmemory.core.ai.AiGatewayPreset;
import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.shared.secret.SecretValue;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.http.client.InetAddressFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public final class AiModelCatalogProbe {

    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final int MAX_MODELS = 500;
    private static final Duration MAX_CONNECT_TIMEOUT =
            Duration.ofSeconds(10);
    private static final Duration MAX_READ_TIMEOUT =
            Duration.ofSeconds(20);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AiGatewayEndpointPolicy endpoints;
    private final ObjectMapper json;

    AiModelCatalogProbe(
            AiGatewayEndpointPolicy endpoints,
            ObjectMapper json) {
        this.endpoints = endpoints;
        this.json = json;
    }

    public Result transientProbe(
            AiGatewayPreset preset,
            AiGatewayProtocol protocol,
            String baseUrl,
            SecretValue credential,
            Duration timeout) {
        String allowed = endpoints.requireAllowed(
                preset,
                protocol,
                baseUrl);
        return probe(
                preset,
                protocol,
                allowed,
                credential,
                timeout);
    }

    public Result storedProbe(
            AiGatewayPreset preset,
            AiGatewayConnection connection) {
        return probe(
                preset,
                connection.protocol(),
                connection.baseUrl(),
                connection.credential(),
                connection.timeout());
    }

    private Result probe(
            AiGatewayPreset preset,
            AiGatewayProtocol protocol,
            String baseUrl,
            SecretValue credential,
            Duration timeout) {
        try {
            HttpClientSettings settings = HttpClientSettings.defaults()
                    .withConnectTimeout(min(timeout, MAX_CONNECT_TIMEOUT))
                    .withReadTimeout(min(timeout, MAX_READ_TIMEOUT))
                    .withRedirects(HttpRedirects.DONT_FOLLOW)
                    .withInetAddressFilter(requiresPublicAddress(preset)
                            ? InetAddressFilter.externalAddresses()
                            : InetAddressFilter.all());
            var requestFactory = ClientHttpRequestFactoryBuilder.jdk()
                    .build(settings);
            RestClient client = RestClient.builder()
                    .requestFactory(requestFactory)
                    .build();
            byte[] body = request(
                            client,
                            protocol,
                            modelsUri(baseUrl, protocol),
                            credential)
                    .exchange((httpRequest, response) -> {
                        int status = response.getStatusCode().value();
                        if (status >= 400) {
                            throw new ProbeHttpException(status);
                        }
                        return response.getBody()
                                .readNBytes(MAX_RESPONSE_BYTES + 1);
                    });
            if (body.length == 0) {
                return Result.failed("empty_response");
            }
            if (body.length > MAX_RESPONSE_BYTES) {
                return Result.failed("response_too_large");
            }
            return Result.authenticated(parseModels(body));
        } catch (RuntimeException failure) {
            return Result.failed(errorCode(failure));
        }
    }

    private RestClient.RequestHeadersSpec<?> request(
            RestClient client,
            AiGatewayProtocol protocol,
            URI uri,
            SecretValue credential) {
        RestClient.RequestHeadersSpec<?> request = client.get()
                .uri(uri)
                .header(HttpHeaders.ACCEPT, "application/json");
        return switch (protocol) {
            case OPENAI_COMPATIBLE -> request.header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + credential.expose());
            case ANTHROPIC_MESSAGES -> request
                    .header("x-api-key", credential.expose())
                    .header("anthropic-version", ANTHROPIC_VERSION);
        };
    }

    private List<ModelRef> parseModels(byte[] body) {
        JsonNode root = json.readTree(body);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            return List.of();
        }
        LinkedHashSet<ModelRef> models = new LinkedHashSet<>();
        for (JsonNode item : data) {
            if (models.size() >= MAX_MODELS) {
                break;
            }
            String id = text(item, "id");
            if (id == null || id.length() > 200) {
                continue;
            }
            String displayName = text(item, "display_name");
            models.add(new ModelRef(
                    id,
                    displayName == null ? id : displayName));
        }
        ArrayList<ModelRef> sorted = new ArrayList<>(models);
        sorted.sort(Comparator.comparing(ModelRef::displayName)
                .thenComparing(ModelRef::id));
        return List.copyOf(sorted);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) {
            return null;
        }
        String normalized = value.asString().strip();
        return normalized.isBlank() ? null : normalized;
    }

    private static URI modelsUri(
            String baseUrl,
            AiGatewayProtocol protocol) {
        String normalized = baseUrl.replaceAll("/+$", "");
        if (protocol == AiGatewayProtocol.ANTHROPIC_MESSAGES
                && !normalized.endsWith("/v1")) {
            normalized += "/v1";
        }
        return URI.create(normalized + "/models");
    }

    private static boolean requiresPublicAddress(AiGatewayPreset preset) {
        return preset == AiGatewayPreset.OPENAI
                || preset == AiGatewayPreset.ANTHROPIC
                || preset == AiGatewayPreset.OPENROUTER;
    }

    private static Duration min(Duration configured, Duration maximum) {
        if (configured == null
                || configured.isZero()
                || configured.isNegative()) {
            return maximum;
        }
        return configured.compareTo(maximum) < 0
                ? configured
                : maximum;
    }

    private static String errorCode(RuntimeException failure) {
        if (failure instanceof ProbeHttpException response) {
            return switch (response.statusCode()) {
                case 401, 403 -> "invalid_credential";
                case 404 -> "models_unsupported";
                default -> "provider_error";
            };
        }
        if (failure instanceof org.springframework.web.client.HttpClientErrorException.Unauthorized
                || failure instanceof org.springframework.web.client.HttpClientErrorException.Forbidden) {
            return "invalid_credential";
        }
        if (failure instanceof org.springframework.web.client.HttpClientErrorException.NotFound) {
            return "models_unsupported";
        }
        if (failure instanceof org.springframework.web.client.ResourceAccessException) {
            return "unreachable";
        }
        return "provider_error";
    }

    private static final class ProbeHttpException extends RuntimeException {

        private final int statusCode;

        private ProbeHttpException(int statusCode) {
            super("AI provider probe returned HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        int statusCode() {
            return statusCode;
        }
    }

    public record ModelRef(String id, String displayName) {
    }

    public record Result(
            boolean authenticated,
            List<ModelRef> models,
            String errorCode) {

        public Result {
            models = models == null ? List.of() : List.copyOf(models);
        }

        static Result authenticated(List<ModelRef> models) {
            return new Result(true, models, null);
        }

        static Result failed(String errorCode) {
            return new Result(false, List.of(), errorCode);
        }
    }
}
