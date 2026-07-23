package com.orgmemory.connectors.googledrive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The Drive v3 API as this adapter needs it: paged listing, metadata export, and content
 * download.
 *
 * <p>Drive differs from Slack in the two ways that shape this class. It reports failures with
 * HTTP status codes rather than inside a 200 body, so a status check is meaningful here in a way
 * it is not there. And it returns only the fields asked for, so the field selector is the
 * contract — asking for permissions inline is what lets one page of files carry its own ACL
 * instead of costing a call per file.
 *
 * <p>Responses are read as a tree rather than mapped onto records, for the same reason as Slack:
 * a handful of fields out of large, evolving objects, and a tree survives Google adding to them.
 */
class GoogleDriveApiClient {

    private static final String BASE_URL = "https://www.googleapis.com/drive/v3";
    private static final int PAGE_SIZE = 100;

    /**
     * What a crawl reads about a file. Permissions travel with the metadata rather than through
     * {@code permissions.list} per file, which would be one request per document.
     */
    static final String FILE_FIELDS =
            "nextPageToken, files(id, name, mimeType, modifiedTime, webViewLink, trashed, "
                    + "owners(emailAddress, displayName), "
                    + "permissions(id, type, emailAddress, domain, role, deleted))";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GoogleAccessTokenSource tokens;

    GoogleDriveApiClient(
            RestClient.Builder restClientBuilder,
            GoogleAccessTokenSource tokens,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.clone().baseUrl(BASE_URL).build();
        this.tokens = tokens;
        this.objectMapper = objectMapper;
    }

    /** Who this credential is acting as, which is what the connection is keyed on. */
    JsonNode about() {
        return get("/about", Map.of("fields", "user(emailAddress, displayName)"));
    }

    /**
     * Every file matching {@code query}, across every page.
     *
     * <p>{@code includeItemsFromAllDrives} has to be paired with {@code supportsAllDrives}, and
     * without both a shared drive is silently invisible rather than refused — a file the crawl
     * never sees is a file the ledger will retire on the next complete crawl.
     */
    List<JsonNode> listFiles(String query, boolean includeSharedDrives, int limit) {
        List<JsonNode> collected = new ArrayList<>();
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
            JsonNode files = body.path("files");
            if (files.isArray()) {
                for (JsonNode file : files) {
                    if (collected.size() >= limit) {
                        return collected;
                    }
                    collected.add(file);
                }
            }
            pageToken = body.path("nextPageToken").asString("");
        } while (!pageToken.isBlank());
        return collected;
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
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath(path);
        parameters.forEach(uri::queryParam);
        Response response = restClient
                .get()
                .uri(uri.build().toUriString())
                // The token travels in the Authorization header rather than a query parameter so
                // it cannot end up in a URI, an access log, or a captured request.
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                .exchange((request, clientResponse) -> new Response(
                        clientResponse.getStatusCode(),
                        new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8)),
                        false);
        if (!response.status().is2xxSuccessful()) {
            throw new GoogleDriveApiException(
                    "Drive returned HTTP " + response.status().value() + " for " + path,
                    reasonOf(response.body()));
        }
        return response.body();
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

    private record Response(HttpStatusCode status, String body) {
    }
}
