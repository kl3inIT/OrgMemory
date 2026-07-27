package com.orgmemory.connectors.github;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The GitHub REST surface used by the connector: app-installation identity, accessible
 * repositories, effective collaborators, and issue/PR descriptions.
 *
 * <p>Pagination follows the source's {@code Link: ... rel="next"} response rather than deriving
 * page numbers. GitHub can change collection sizes while a crawl runs; the link is the source's
 * continuation contract and the only value this client treats as authoritative.
 *
 * <p>Rate limits, dropped connections, and server errors are retried with a bound. A plain 403
 * is a permission refusal and is not retried; a 403 with zero remaining requests or
 * {@code Retry-After} is a transient limit.
 */
final class GitHubApiClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_ATTEMPTS = 4;
    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
    private static final Duration BASE_BACKOFF = Duration.ofMillis(300);
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);

    record PagedItems(List<JsonNode> items, boolean truncated) {

        PagedItems {
            items = List.copyOf(items);
        }
    }

    interface Sleeper {
        void sleep(Duration duration);
    }

    private final RestClient restClient;
    private final GitHubInstallationTokenSource tokens;
    private final ObjectMapper objectMapper;
    private final Sleeper sleeper;

    GitHubApiClient(
            RestClient.Builder restClientBuilder,
            GitHubInstallationTokenSource tokens,
            ObjectMapper objectMapper) {
        this(restClientBuilder, tokens, objectMapper, GitHubApiClient::sleepFor);
    }

    GitHubApiClient(
            RestClient.Builder restClientBuilder,
            GitHubInstallationTokenSource tokens,
            ObjectMapper objectMapper,
            Sleeper sleeper) {
        this.restClient = restClientBuilder.clone()
                .baseUrl(GitHubInstallationTokenSource.BASE_URL)
                .build();
        this.tokens = tokens;
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
    }

    /** Installation account and granted permission metadata, authenticated with the app JWT. */
    JsonNode installation() {
        return getJson(
                "/app/installations/" + tokens.installationId(),
                Map.of(),
                tokens.appJwt(),
                false);
    }

    /** Every repository selected for this installation. */
    List<JsonNode> repositories() {
        return collectPaged("/installation/repositories", Map.of(), "repositories", Integer.MAX_VALUE)
                .items();
    }

    /**
     * GitHub's fully resolved readers: direct collaborators, team-derived members, organization
     * defaults, owners, and enterprise roles.
     */
    List<JsonNode> collaborators(String owner, String repository) {
        return collectPaged(
                        "/repos/" + segment(owner) + "/" + segment(repository) + "/collaborators",
                        Map.of("affiliation", "all"),
                        null,
                        Integer.MAX_VALUE)
                .items();
    }

    /** Issues and pull requests are one GitHub Issues collection; the pull_request field tags PRs. */
    PagedItems issues(String owner, String repository, int limit) {
        return collectPaged(
                "/repos/" + segment(owner) + "/" + segment(repository) + "/issues",
                Map.of(
                        "state", "all",
                        "sort", "updated",
                        "direction", "asc"),
                null,
                limit);
    }

    private PagedItems collectPaged(
            String path,
            Map<String, String> parameters,
            String arrayField,
            int limit) {
        List<JsonNode> collected = new ArrayList<>();
        String next = requestUri(path, parameters);
        while (next != null) {
            Response response = get(next, tokens.accessToken(), true);
            JsonNode root = parse(response.body(), path);
            JsonNode page = arrayField == null ? root : root.path(arrayField);
            if (!page.isArray()) {
                throw new GitHubApiException(
                        "GitHub returned no expected collection for " + path,
                        "unreadable_response");
            }
            for (JsonNode item : page) {
                if (collected.size() >= limit) {
                    return new PagedItems(collected, true);
                }
                collected.add(item);
            }
            String linked = nextLink(response.headers());
            if (collected.size() >= limit && linked != null) {
                return new PagedItems(collected, true);
            }
            next = linked == null ? null : requireGitHubUri(linked);
        }
        return new PagedItems(collected, false);
    }

    private JsonNode getJson(
            String path,
            Map<String, String> parameters,
            String bearer,
            boolean installationToken) {
        return parse(get(requestUri(path, parameters), bearer, installationToken).body(), path);
    }

    private Response get(String uri, String bearer, boolean installationToken) {
        String currentBearer = bearer;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Response response;
            try {
                response = execute(uri, currentBearer);
            } catch (ResourceAccessException dropped) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new GitHubApiException(
                            "GitHub was unreachable after " + MAX_ATTEMPTS + " attempts",
                            "unreachable");
                }
                sleeper.sleep(backoffFor(attempt));
                continue;
            }
            if (response.status().is2xxSuccessful()) {
                return response;
            }
            if (response.status().value() == 401 && installationToken && attempt == 1) {
                tokens.invalidate();
                currentBearer = tokens.accessToken();
                continue;
            }
            if (!isRetryable(response) || attempt == MAX_ATTEMPTS) {
                throw new GitHubApiException(
                        "GitHub returned HTTP " + response.status().value() + " for " + safePath(uri),
                        errorCode(response.status()));
            }
            sleeper.sleep(delayFor(attempt, response.headers()));
        }
        throw new IllegalStateException("unreachable: every GitHub attempt returns or throws");
    }

    private Response execute(String uri, String bearer) {
        return restClient
                .get()
                .uri(URI.create(uri))
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .header("X-GitHub-Api-Version", GitHubInstallationTokenSource.API_VERSION)
                .exchange((request, response) -> new Response(
                        response.getStatusCode(),
                        response.getHeaders(),
                        new String(readBounded(response.getBody()), StandardCharsets.UTF_8)),
                        false);
    }

    private JsonNode parse(String body, String path) {
        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException unreadable) {
            throw new GitHubApiException(
                    "GitHub returned an unreadable body for " + path,
                    "unreadable_response");
        }
    }

    private static String requestUri(String path, Map<String, String> parameters) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(
                GitHubInstallationTokenSource.BASE_URL + path);
        Map<String, String> page = new LinkedHashMap<>(parameters);
        page.putIfAbsent("per_page", String.valueOf(PAGE_SIZE));
        page.forEach(uri::queryParam);
        return uri.build().toUriString();
    }

    private static String nextLink(HttpHeaders headers) {
        List<String> links = headers.get(HttpHeaders.LINK);
        if (links == null) {
            return null;
        }
        for (String header : links) {
            for (String link : header.split(",")) {
                String part = link.trim();
                if (!part.contains("rel=\"next\"")) {
                    continue;
                }
                int start = part.indexOf('<');
                int end = part.indexOf('>', start + 1);
                if (start >= 0 && end > start + 1) {
                    return part.substring(start + 1, end);
                }
            }
        }
        return null;
    }

    private static boolean isRetryable(Response response) {
        int status = response.status().value();
        if (status == 429 || response.status().is5xxServerError()) {
            return true;
        }
        return status == 403
                && (response.headers().getFirst(HttpHeaders.RETRY_AFTER) != null
                        || "0".equals(response.headers().getFirst("X-RateLimit-Remaining")));
    }

    private static Duration delayFor(int attempt, HttpHeaders headers) {
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                Duration asked = Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
                if (!asked.isNegative()) {
                    return asked.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : asked;
                }
            } catch (NumberFormatException httpDate) {
                // Fall through to bounded exponential backoff.
            }
        }
        return backoffFor(attempt);
    }

    private static Duration backoffFor(int attempt) {
        return BASE_BACKOFF.multipliedBy(1L << (attempt - 1));
    }

    private static byte[] readBounded(InputStream body) throws IOException {
        byte[] read = body.readNBytes(MAX_BODY_BYTES + 1);
        if (read.length > MAX_BODY_BYTES) {
            throw new GitHubApiException(
                    "GitHub returned a response larger than " + MAX_BODY_BYTES + " bytes",
                    "response_too_large");
        }
        return read;
    }

    private static String errorCode(HttpStatusCode status) {
        return "github_http_" + status.value();
    }

    private static String safePath(String uri) {
        URI parsed = URI.create(uri);
        return parsed.getPath();
    }

    private static String requireGitHubUri(String uri) {
        URI parsed;
        try {
            parsed = URI.create(uri);
        } catch (IllegalArgumentException invalid) {
            throw new GitHubApiException(
                    "GitHub returned an invalid pagination link",
                    "unreadable_response");
        }
        boolean defaultPort = parsed.getPort() == -1 || parsed.getPort() == 443;
        if (!"https".equalsIgnoreCase(parsed.getScheme())
                || !"api.github.com".equalsIgnoreCase(parsed.getHost())
                || !defaultPort
                || parsed.getUserInfo() != null) {
            throw new GitHubApiException(
                    "GitHub returned a pagination link outside api.github.com",
                    "unreadable_response");
        }
        return parsed.toString();
    }

    private static String segment(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9_.-]+")
                || ".".equals(value)
                || "..".equals(value)) {
            throw new IllegalArgumentException("GitHub path segment is invalid");
        }
        return value;
    }

    private static void sleepFor(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GitHubApiException(
                    "Interrupted while waiting to retry GitHub",
                    "interrupted");
        }
    }

    private record Response(HttpStatusCode status, HttpHeaders headers, String body) {
    }
}
