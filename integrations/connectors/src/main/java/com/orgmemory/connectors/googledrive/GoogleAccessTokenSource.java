package com.orgmemory.connectors.googledrive;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a service account key into an access token, and keeps it until it expires.
 *
 * <p>This is the OAuth 2.0 JWT bearer grant: sign a short-lived assertion with the account's
 * private key, hand it to Google, get a token. No dependency is needed for it — the JDK signs
 * {@code SHA256withRSA} and encodes base64url, and Jackson is already here. Pulling in an SDK
 * to do three lines of signing would put a large transitive surface behind a credential path,
 * which is the last place to accept one.
 *
 * <p>An authorization code flow would need a redirect endpoint, a registered client and a
 * refresh token to keep — three things a server-side crawl has no use for, in exchange for a
 * credential a person has to re-consent to.
 *
 * <p>Tokens are cached with a margin, because a token that expires between the check and the
 * request is a failure that looks like a permission problem.
 */
class GoogleAccessTokenSource {

    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final Duration ASSERTION_LIFETIME = Duration.ofMinutes(60);
    /** Renew this far ahead of expiry rather than at it. */
    private static final Duration RENEW_MARGIN = Duration.ofMinutes(2);

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final GoogleServiceAccountKey key;
    private final String scope;
    private final String impersonatedUser;
    private final Clock clock;

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    GoogleAccessTokenSource(
            RestClient.Builder restClientBuilder,
            GoogleServiceAccountKey key,
            String scope,
            String impersonatedUser,
            Clock clock) {
        this.restClient = restClientBuilder.clone().baseUrl(key.tokenUri()).build();
        this.key = key;
        this.scope = scope;
        this.impersonatedUser = impersonatedUser == null || impersonatedUser.isBlank()
                ? null
                : impersonatedUser.trim();
        this.clock = clock;
    }

    synchronized String accessToken() {
        Instant now = clock.instant();
        if (token != null && now.isBefore(tokenExpiresAt.minus(RENEW_MARGIN))) {
            return token;
        }
        JsonNode granted = exchange(assertion(now));
        token = granted.path("access_token").asString("");
        if (token.isBlank()) {
            throw new GoogleDriveCredentialException(
                    "Google returned no access token for this key", "invalid_grant");
        }
        tokenExpiresAt = now.plusSeconds(Math.max(60, granted.path("expires_in").asLong(3600)));
        return token;
    }

    private JsonNode exchange(String assertion) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE);
        form.add("assertion", assertion);
        try {
            String body = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return MAPPER.readTree(body == null ? "{}" : body);
        } catch (RestClientException refused) {
            // Google answers 4xx with {"error":"invalid_grant","error_description":"..."}, and
            // the code is what an administrator can look up. The description is not repeated:
            // it is Google's prose about our assertion, and the assertion was signed with the key.
            throw new GoogleDriveCredentialException(
                    "Google refused the service account assertion", errorCodeOf(refused));
        }
    }

    private static String errorCodeOf(RestClientException refused) {
        String message = refused.getMessage();
        if (message == null) {
            return "unreachable";
        }
        for (String known : new String[] {"invalid_grant", "unauthorized_client", "invalid_client"}) {
            if (message.contains(known)) {
                return known;
            }
        }
        return "invalid_grant";
    }

    /**
     * The signed assertion. {@code sub} is present only with domain-wide delegation: without it
     * the account acts as itself and sees what has been shared with it, which is the narrower
     * and safer arrangement.
     */
    private String assertion(Instant now) {
        String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        StringBuilder claims = new StringBuilder("{")
                .append("\"iss\":\"").append(key.clientEmail()).append("\",")
                .append("\"scope\":\"").append(scope).append("\",")
                .append("\"aud\":\"").append(key.tokenUri()).append("\",")
                .append("\"iat\":").append(now.getEpochSecond()).append(',')
                .append("\"exp\":").append(now.plus(ASSERTION_LIFETIME).getEpochSecond());
        if (impersonatedUser != null) {
            claims.append(",\"sub\":\"").append(impersonatedUser).append('"');
        }
        claims.append('}');

        String signingInput = header + "." + encode(claims.toString());
        return signingInput + "." + BASE64_URL.encodeToString(sign(signingInput));
    }

    private byte[] sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key.privateKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (GeneralSecurityException unusable) {
            throw new GoogleDriveCredentialException(
                    "The service account key could not sign an assertion", "invalid_key");
        }
    }

    private static String encode(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
