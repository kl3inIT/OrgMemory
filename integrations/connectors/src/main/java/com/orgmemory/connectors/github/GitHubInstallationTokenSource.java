package com.orgmemory.connectors.github;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Signs short GitHub App JWTs and exchanges them for one-hour installation access tokens.
 *
 * <p>The JWT follows GitHub's current contract: RS256, an issued-at time backdated for clock
 * drift, and an expiry below the ten-minute ceiling. Installation tokens are cached with a
 * margin so a request never starts with a token about to expire.
 */
final class GitHubInstallationTokenSource {

    static final String BASE_URL = "https://api.github.com";
    static final String API_VERSION = "2026-03-10";

    private static final Duration CLOCK_DRIFT = Duration.ofSeconds(60);
    private static final Duration JWT_LIFETIME = Duration.ofMinutes(9);
    private static final Duration RENEW_MARGIN = Duration.ofMinutes(2);
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final RestClient restClient;
    private final GitHubAppKey key;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    GitHubInstallationTokenSource(
            RestClient.Builder restClientBuilder,
            GitHubAppKey key,
            ObjectMapper objectMapper,
            Clock clock) {
        this.restClient = restClientBuilder.clone().baseUrl(BASE_URL).build();
        this.key = key;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    synchronized String accessToken() {
        Instant now = clock.instant();
        if (token != null && now.isBefore(tokenExpiresAt.minus(RENEW_MARGIN))) {
            return token;
        }
        JsonNode granted = exchange(appJwt(now));
        String next = granted.path("token").asString("");
        if (next.isBlank()) {
            throw new GitHubCredentialException(
                    "GitHub returned no installation access token", "invalid_installation");
        }
        token = next;
        tokenExpiresAt = expiresAt(granted.path("expires_at").asString(""), now);
        return token;
    }

    synchronized void invalidate() {
        token = null;
        tokenExpiresAt = Instant.EPOCH;
    }

    String appJwt() {
        return appJwt(clock.instant());
    }

    long installationId() {
        return key.installationId();
    }

    private JsonNode exchange(String jwt) {
        try {
            String body = restClient.post()
                    .uri("/app/installations/{installationId}/access_tokens", key.installationId())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (RuntimeException refused) {
            throw new GitHubCredentialException(
                    "GitHub refused the app installation credential", "invalid_installation");
        }
    }

    private String appJwt(Instant now) {
        String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        Instant issuedAt = now.minus(CLOCK_DRIFT);
        String claims = "{\"iat\":" + issuedAt.getEpochSecond()
                + ",\"exp\":" + issuedAt.plus(JWT_LIFETIME).getEpochSecond()
                + ",\"iss\":\"" + jsonEscape(key.appId()) + "\"}";
        String signingInput = header + "." + encode(claims);
        return signingInput + "." + BASE64_URL.encodeToString(sign(signingInput));
    }

    private byte[] sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key.privateKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (GeneralSecurityException unusable) {
            throw new GitHubCredentialException(
                    "The GitHub App key could not sign a JWT", "invalid_key");
        }
    }

    private static Instant expiresAt(String value, Instant now) {
        try {
            Instant parsed = Instant.parse(value);
            if (!parsed.isAfter(now)) {
                throw new GitHubCredentialException(
                        "GitHub returned an expired installation access token",
                        "invalid_installation");
            }
            return parsed;
        } catch (DateTimeParseException unreadable) {
            throw new GitHubCredentialException(
                    "GitHub returned no readable installation token expiry",
                    "invalid_installation");
        }
    }

    private static String encode(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
