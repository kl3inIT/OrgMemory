package com.orgmemory.connectors.googledrive;

import java.io.IOException;
import java.io.InputStream;
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
 * The Drive v3 API as this adapter needs it: paged listing, per-file permissions, metadata
 * export, and content download.
 *
 * <p>Drive differs from Slack in the two ways that shape this class. It reports failures with
 * HTTP status codes rather than inside a 200 body, so a status check is meaningful here in a way
 * it is not there. And it returns only the fields asked for, so the field selector is the
 * contract — asking for permissions inline is what lets one page of files carry its own ACL
 * instead of costing a call per file. Shared-drive items are the exception: Drive refuses to
 * inline their permissions and returns only {@code permissionIds}, which is why
 * {@link #listPermissions(String)} exists.
 *
 * <p>Two refusals Google makes routinely are retried rather than surfaced: a rate limit (429, or
 * a 403 whose reason says so) waits out {@code Retry-After}, and a 5xx or a dropped connection
 * backs off and tries again. Both are bounded — a quota that stays exhausted becomes the
 * connection's failure, not the worker's stall.
 *
 * <p>Responses are read as a tree rather than mapped onto records, for the same reason as Slack:
 * a handful of fields out of large, evolving objects, and a tree survives Google adding to them.
 */
class GoogleDriveApiClient {

    private static final String BASE_URL = "https://www.googleapis.com/drive/v3";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_ATTEMPTS = 4;
    private static final Duration BASE_BACKOFF = Duration.ofMillis(300);
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);
    /**
     * No response is read past this, whatever the metadata claimed. A crawl holds one body in
     * memory at a time, so this is the worker's ceiling for a single document, not a budget.
     */
    static final int MAX_BODY_BYTES = 25 * 1024 * 1024;

    /**
     * What a crawl reads about a file. Permissions travel with the metadata rather than through
     * {@code permissions.list} per file, which would be one request per document —
     * {@code permissionIds} rides along so a shared-drive item, which never carries inline
     * permissions, is recognizable as "unresolved" rather than mistaken for "shared with nobody".
     * {@code incompleteSearch} is the listing confessing it did not search everything, which the
     * completeness claim has to hear about.
     */
    static final String FILE_FIELDS =
            "nextPageToken, incompleteSearch, files(id, name, mimeType, modifiedTime, webViewLink, "
                    + "trashed, driveId, size, owners(emailAddress, displayName), permissionIds, "
                    + "permissions(id, type, emailAddress, domain, role, deleted))";

    private static final String PERMISSION_FIELDS =
            "nextPageToken, permissions(id, type, emailAddress, domain, role, deleted)";

    /** One page of {@code files.list}, with whether Google admitted the search was incomplete. */
    record FileListing(List<JsonNode> files, boolean incompleteSearch) {
    }

    /** How a retry waits, injectable so a test asserts the waits instead of serving them. */
    interface Sleeper {
        void sleep(Duration duration);
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GoogleAccessTokenSource tokens;
    private final Sleeper sleeper;

    GoogleDriveApiClient(
            RestClient.Builder restClientBuilder,
            GoogleAccessTokenSource tokens,
            ObjectMapper objectMapper) {
        this(restClientBuilder, tokens, objectMapper, GoogleDriveApiClient::sleepFor);
    }

    GoogleDriveApiClient(
            RestClient.Builder restClientBuilder,
            GoogleAccessTokenSource tokens,
            ObjectMapper objectMapper,
            Sleeper sleeper) {
        this.restClient = restClientBuilder.clone().baseUrl(BASE_URL).build();
        this.tokens = tokens;
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
    }

    /** Who this credential is acting as, which is what the connection is keyed on. */
    JsonNode about() {
        return get("/about", Map.of("fields", "user(emailAddress, displayName)"));
    }

    /**
     * Every file matching {@code query}, across every page, up to {@code limit}.
     *
     * <p>{@code includeItemsFromAllDrives} has to be paired with {@code supportsAllDrives}, and
     * without both a shared drive is silently invisible rather than refused — a file the crawl
     * never sees is a file the ledger will retire on the next complete crawl.
     */
    FileListing listFiles(String query, boolean includeSharedDrives, int limit) {
        List<JsonNode> collected = new ArrayList<>();
        boolean incompleteSearch = false;
        String pageToken = null;
        do {
            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.put("q", query);
            parameters.put("fields", FILE_FIELDS);
            parameters.put("pageSize", String.valueOf(Math.min(PAGE_SIZE, limit)));
            parameters.put("supportsAllDrives", String.valueOf(includeSharedDrives));
            parameters.put("includeItemsFromAllDrives", String.valueOf(includeSharedDrives));
            if (includeSharedDrives) {
                parameters.put("corpora", "allDrives");
            }
            if (pageToken != null) {
                parameters.put("pageToken", pageToken);
            }
            JsonNode body = get("/files", parameters);
            incompleteSearch |= body.path("incompleteSearch").asBoolean(false);
            JsonNode files = body.path("files");
            if (files.isArray()) {
                for (JsonNode file : files) {
                    if (collected.size() >= limit) {
                        return new FileListing(collected, incompleteSearch);
                    }
                    collected.add(file);
                }
            }
            pageToken = body.path("nextPageToken").asString("");
        } while (!pageToken.isBlank());
        return new FileListing(collected, incompleteSearch);
    }

    /**
     * One file's full sharing, for the items — shared-drive ones — whose listing carried only
     * {@code permissionIds}. The shape of each permission matches the inline form, so the mapper
     * does not care where a permission came from.
     */
    List<JsonNode> listPermissions(String fileId) {
        List<JsonNode> permissions = new ArrayList<>();
        String pageToken = null;
        do {
            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.put("fields", PERMISSION_FIELDS);
            parameters.put("pageSize", String.valueOf(PAGE_SIZE));
            parameters.put("supportsAllDrives", "true");
            if (pageToken != null) {
                parameters.put("pageToken", pageToken);
            }
            JsonNode body = get("/files/" + fileId + "/permissions", parameters);
            JsonNode page = body.path("permissions");
            if (page.isArray()) {
                page.forEach(permissions::add);
            }
            pageToken = body.path("nextPageToken").asString("");
        } while (!pageToken.isBlank());
        return permissions;
    }

    /** A Google-native document converted to text. */
    String exportText(String fileId, String targetMediaType) {
        return getText("/files/" + fileId + "/export", Map.of("mimeType", targetMediaType));
    }

    /** A file stored as itself, downloaded rather than converted. */
    String downloadText(String fileId) {
        return getText("/files/" + fileId, Map.of("alt", "media", "supportsAllDrives", "true"));
    }

    private JsonNode get(String path, Map<String, String> parameters) {
        String body = getText(path, parameters);
        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException unreadable) {
            throw new GoogleDriveApiException("Drive returned an unreadable body for " + path);
        }
    }

    private String getText(String path, Map<String, String> parameters) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Response response;
            try {
                response = execute(path, parameters);
            } catch (ResourceAccessException dropped) {
                // A connection that timed out or reset mid-crawl says nothing about the file; the
                // request is simply made again. A network that stays down exhausts the attempts
                // and fails the crawl honestly.
                if (attempt == MAX_ATTEMPTS) {
                    throw new GoogleDriveApiException(
                            "Drive was unreachable for " + path + " after " + MAX_ATTEMPTS + " attempts");
                }
                sleeper.sleep(backoffFor(attempt));
                continue;
            }
            if (response.status().is2xxSuccessful()) {
                return response.body();
            }
            String reason = reasonOf(response.body());
            if (!isRetryable(response.status(), reason) || attempt == MAX_ATTEMPTS) {
                throw new GoogleDriveApiException(
                        "Drive returned HTTP " + response.status().value() + " for " + path, reason);
            }
            sleeper.sleep(delayFor(attempt, response.retryAfterSeconds()));
        }
        throw new IllegalStateException("unreachable: every attempt returns or throws");
    }

    private Response execute(String path, Map<String, String> parameters) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath(path);
        parameters.forEach(uri::queryParam);
        return restClient
                .get()
                .uri(uri.build().toUriString())
                // The token travels in the Authorization header rather than a query parameter so
                // it cannot end up in a URI, an access log, or a captured request.
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                .exchange((request, clientResponse) -> new Response(
                        clientResponse.getStatusCode(),
                        new String(readBounded(clientResponse.getBody()), StandardCharsets.UTF_8),
                        clientResponse.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)),
                        false);
    }

    /**
     * Reads at most {@link #MAX_BODY_BYTES}. The metadata {@code size} is checked before a
     * download is attempted at all, but metadata can be absent (exports) or wrong, and a bound
     * enforced while reading is the only one that actually protects the worker.
     */
    private static byte[] readBounded(InputStream body) throws IOException {
        byte[] read = body.readNBytes(MAX_BODY_BYTES + 1);
        if (read.length > MAX_BODY_BYTES) {
            throw new GoogleDriveApiException(
                    "Drive returned a body larger than " + MAX_BODY_BYTES + " bytes");
        }
        return read;
    }

    /**
     * What is worth trying again: an explicit rate limit, a server error, and Google's habit of
     * reporting a rate limit as a 403 whose reason says so. A plain 403 or 404 is an answer about
     * the file, not about the moment, and is not retried.
     */
    private static boolean isRetryable(HttpStatusCode status, String reason) {
        if (status.value() == 429 || status.is5xxServerError()) {
            return true;
        }
        return status.value() == 403
                && ("userRateLimitExceeded".equals(reason) || "rateLimitExceeded".equals(reason));
    }

    private static Duration delayFor(int attempt, Long retryAfterSeconds) {
        if (retryAfterSeconds != null && retryAfterSeconds >= 0) {
            Duration asked = Duration.ofSeconds(retryAfterSeconds);
            return asked.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : asked;
        }
        return backoffFor(attempt);
    }

    private static Duration backoffFor(int attempt) {
        return BASE_BACKOFF.multipliedBy(1L << (attempt - 1));
    }

    private static void sleepFor(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GoogleDriveApiException("interrupted while waiting to retry a Drive call");
        }
    }

    /**
     * Google's own reason for refusing, out of its error envelope. The reason rather than the
     * message, because the reason is what an administrator can look up and the message is prose
     * that changes.
     */
    private String reasonOf(String body) {
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            JsonNode firstReason = error.path("errors").path(0).path("reason");
            if (!firstReason.isMissingNode() && !firstReason.asString("").isBlank()) {
                return firstReason.asString("");
            }
            String status = error.path("status").asString("");
            return status.isBlank() ? null : status;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private record Response(HttpStatusCode status, String body, String retryAfterHeader) {

        /** {@code Retry-After} as seconds, or null when absent or an HTTP-date this ignores. */
        Long retryAfterSeconds() {
            if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
                return null;
            }
            try {
                return Long.parseLong(retryAfterHeader.strip());
            } catch (NumberFormatException httpDate) {
                return null;
            }
        }
    }
}
