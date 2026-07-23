package com.orgmemory.connectors.slack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The Slack Web API as this adapter needs it: cursor pagination, rate limits, and the envelope
 * every method shares.
 *
 * <p>Two things about that envelope drive the design. Slack answers {@code 200 OK} for logical
 * failures and reports them in an {@code ok} field, so a status check alone would read
 * {@code missing_scope} as success. And a paginated method reports continuation through
 * {@code response_metadata.next_cursor}, which is absent or empty on the last page rather than
 * signalled any other way.
 *
 * <p>Responses are read as a tree rather than mapped onto records. The adapter wants a handful
 * of fields out of large, evolving objects, and a tree survives Slack adding to them.
 */
class SlackWebApiClient {

    private static final String BASE_URL = "https://slack.com/api";
    private static final int PAGE_LIMIT = 200;
    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(1);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SlackRateLimitGate rateLimits;
    private final String botToken;

    SlackWebApiClient(RestClient.Builder restClientBuilder, String botToken) {
        this(restClientBuilder, botToken, new ObjectMapper(), new SlackRateLimitGate());
    }

    SlackWebApiClient(
            RestClient.Builder restClientBuilder,
            String botToken,
            ObjectMapper objectMapper,
            SlackRateLimitGate rateLimits) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.objectMapper = objectMapper;
        this.rateLimits = rateLimits;
        this.botToken = botToken;
    }

    /**
     * Every element of {@code arrayField} across every page of {@code method}.
     *
     * <p>A page limit is requested but never assumed: Slack may return fewer, and the only
     * reliable end-of-collection signal is an empty next cursor.
     */
    List<JsonNode> collectPaged(String method, Map<String, String> parameters, String arrayField) {
        List<JsonNode> collected = new ArrayList<>();
        String cursor = null;
        do {
            Map<String, String> page = new LinkedHashMap<>(parameters);
            page.put("limit", String.valueOf(PAGE_LIMIT));
            if (cursor != null && !cursor.isBlank()) {
                page.put("cursor", cursor);
            }
            JsonNode body = call(method, page);
            JsonNode array = body.path(arrayField);
            if (array.isArray()) {
                array.forEach(collected::add);
            }
            cursor = body.path("response_metadata").path("next_cursor").asString("");
        } while (cursor != null && !cursor.isBlank());
        return collected;
    }

    /**
     * One call, waiting out any rate limit already in force and honouring a new one. A limit is
     * not a failure to report: the request is held and re-sent, and only an exhausted retry
     * budget surfaces as an exception.
     */
    JsonNode call(String method, Map<String, String> parameters) {
        for (int attempt = 1; ; attempt++) {
            rateLimits.awaitOpen();
            Response response = send(method, parameters);
            if (response.status().value() == 429) {
                if (attempt > MAX_RATE_LIMIT_RETRIES) {
                    throw new SlackApiException(
                            "Slack kept rate limiting " + method + " after " + MAX_RATE_LIMIT_RETRIES
                                    + " waits",
                            "ratelimited");
                }
                rateLimits.closeFor(retryAfter(response.headers()));
                continue;
            }
            if (!response.status().is2xxSuccessful()) {
                throw new SlackApiException(
                        "Slack returned HTTP " + response.status().value() + " for " + method);
            }
            return requireOk(method, response.body());
        }
    }

    private Response send(String method, Map<String, String> parameters) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        parameters.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                form.add(key, value);
            }
        });
        return restClient
                .post()
                .uri(UriComponentsBuilder.fromPath("/" + method).build().toUriString())
                // The token travels in the Authorization header rather than the form body so it
                // cannot end up in a URI, an access log, or a captured request payload.
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + botToken)
                .body(form)
                .exchange((request, response) -> new Response(
                        response.getStatusCode(),
                        response.getHeaders(),
                        new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)),
                        false);
    }

    /**
     * Slack reports refusals inside a 200 response, so success is {@code ok: true} and nothing
     * else. The error code is carried through because the caller's reaction differs: a missing
     * scope is a configuration problem, {@code not_in_channel} is one channel to skip.
     */
    private JsonNode requireOk(String method, String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (RuntimeException malformed) {
            throw new SlackApiException("Slack returned an unreadable body for " + method);
        }
        if (!root.path("ok").asBoolean(false)) {
            String error = root.path("error").asString("unknown_error");
            throw new SlackApiException("Slack refused " + method + ": " + error, error);
        }
        return root;
    }

    /** Slack states the wait in whole seconds; an absent or unreadable value backs off briefly. */
    private static Duration retryAfter(HttpHeaders headers) {
        String value = headers.getFirst("Retry-After");
        if (value == null || value.isBlank()) {
            return DEFAULT_RETRY_AFTER;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds <= 0 ? DEFAULT_RETRY_AFTER : Duration.ofSeconds(seconds);
        } catch (NumberFormatException notASecondsValue) {
            return DEFAULT_RETRY_AFTER;
        }
    }

    private record Response(HttpStatusCode status, HttpHeaders headers, String body) {
    }
}
