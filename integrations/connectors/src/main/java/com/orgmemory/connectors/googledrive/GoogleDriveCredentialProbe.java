package com.orgmemory.connectors.googledrive;

import com.orgmemory.core.knowledge.ConnectorCredentialProbe;
import com.orgmemory.core.knowledge.ConnectorCredentialProbeResult;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Answers what a service account key actually is, before anything is stored or crawled.
 *
 * <p>Three checks, and the third is the one that earns its keep. Parsing the key catches a
 * pasted wrong file. Exchanging it catches a deleted account, a clock too far off, or delegation
 * that was never granted. Neither says whether the account can see a single document — a service
 * account nobody has shared anything with authenticates perfectly and then indexes nothing, and
 * discovering that hours later as an empty index is exactly the failure this exists to prevent.
 *
 * <p>The domain it reports is the connection key, so an administrator pastes a key and is told
 * the key rather than choosing one.
 */
public class GoogleDriveCredentialProbe implements ConnectorCredentialProbe {

    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GoogleDriveCredentialProbe(RestClient.Builder restClientBuilder) {
        this(restClientBuilder, new ObjectMapper(), Clock.systemUTC());
    }

    GoogleDriveCredentialProbe(
            RestClient.Builder restClientBuilder, ObjectMapper objectMapper, Clock clock) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String sourceSystem() {
        return GoogleDriveSourceProfile.SOURCE_SYSTEM;
    }

    @Override
    public ConnectorCredentialProbeResult probe(SecretValue serviceAccountKey) {
        Objects.requireNonNull(serviceAccountKey, "serviceAccountKey");

        GoogleDriveApiClient client;
        JsonNode about;
        try {
            GoogleServiceAccountKey key = GoogleServiceAccountKey.parse(serviceAccountKey.expose());
            // No impersonation while probing. Checking as the account itself answers "is this key
            // usable", and a delegation misconfiguration would otherwise be reported as a bad key.
            client = new GoogleDriveApiClient(
                    restClientBuilder,
                    new GoogleAccessTokenSource(restClientBuilder, key, DRIVE_SCOPE, null, clock),
                    objectMapper);
            about = client.about();
        } catch (GoogleDriveCredentialException | GoogleDriveApiException | RestClientException refused) {
            return ConnectorCredentialProbeResult.rejected(errorCodeOf(refused));
        }

        String email = about.path("user").path("emailAddress").asString("");
        String displayName = about.path("user").path("displayName").asString(email);
        String domain = domainOf(email);

        try {
            // One file rather than a page: this asks whether the account can see anything at all,
            // not what is in the Drive.
            List<JsonNode> visible = client.listFiles("trashed = false", true, 1);
            if (visible.isEmpty()) {
                return ConnectorCredentialProbeResult.withoutContentAccess(
                        domain, domain, displayName, "access_denied");
            }
        } catch (GoogleDriveApiException | RestClientException refused) {
            return ConnectorCredentialProbeResult.withoutContentAccess(
                    domain, domain, displayName, errorCodeOf(refused));
        }
        return ConnectorCredentialProbeResult.usable(domain, domain, displayName);
    }

    /**
     * The connection is one Google Workspace, which parallels one Slack workspace, so the key is
     * the domain. A service account outside a Workspace has none, and falls back to its own
     * address rather than being refused — it is still a connection, just an unusual one.
     */
    private static String domainOf(String email) {
        int at = email.lastIndexOf('@');
        return at >= 0 && at < email.length() - 1 ? email.substring(at + 1) : email;
    }

    private static String errorCodeOf(RuntimeException failure) {
        if (failure instanceof GoogleDriveCredentialException refused) {
            return refused.errorCode();
        }
        if (failure instanceof GoogleDriveApiException refused && refused.errorCode() != null) {
            return refused.errorCode();
        }
        return failure instanceof GoogleDriveApiException ? "unreadable_response" : "unreachable";
    }
}
