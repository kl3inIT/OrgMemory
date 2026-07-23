package com.orgmemory.connectors.slack;

import com.orgmemory.core.shared.secret.SecretValue;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Answers what a bot token actually is, before anything is stored or crawled.
 *
 * <p>Two calls, and the second is the one that earns its keep. Onyx checks {@code auth.test} and
 * then lists a single channel, and the reason is that the first call cannot fail for a missing
 * scope: a token installed without {@code channels:read} authenticates perfectly and then returns
 * {@code missing_scope} on the first real request, hours later, as an indexing failure nobody
 * connects to the day it was configured. Listing one channel costs one call and moves that
 * discovery to the moment an administrator is looking at the screen.
 *
 * <p>The probe is total: a refusal, a revoked token, and an unreachable Slack are all results
 * rather than exceptions, because the caller asked a question whose negative answer is ordinary.
 */
public class SlackCredentialProbe {

    private final RestClient.Builder restClientBuilder;

    public SlackCredentialProbe(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public SlackCredentialProbeResult probe(SecretValue botToken) {
        Objects.requireNonNull(botToken, "botToken");
        SlackWebApiClient client = new SlackWebApiClient(restClientBuilder, botToken.expose());

        JsonNode auth;
        try {
            auth = client.call("auth.test", Map.of());
        } catch (SlackApiException | RestClientException refused) {
            return SlackCredentialProbeResult.rejected(errorCodeOf(refused));
        }
        String workspaceName = auth.path("team").asString("");
        String workspaceId = auth.path("team_id").asString("");
        String botName = auth.path("user").asString("");

        try {
            // limit=1 rather than a page: this asks whether the scope exists, not what is in it.
            client.call("conversations.list", Map.of("types", "public_channel", "limit", "1"));
        } catch (SlackApiException | RestClientException refused) {
            return SlackCredentialProbeResult.withoutChannelAccess(
                    workspaceName, workspaceId, botName, errorCodeOf(refused));
        }
        return SlackCredentialProbeResult.usable(workspaceName, workspaceId, botName);
    }

    /**
     * Slack's own code where there is one. A transport failure has none, and it is reported as
     * such rather than borrowed from the exception message, which would put our wording where a
     * caller expects Slack's vocabulary.
     */
    private static String errorCodeOf(RuntimeException failure) {
        if (failure instanceof SlackApiException slack && slack.errorCode() != null) {
            return slack.errorCode();
        }
        return failure instanceof SlackApiException ? "unreadable_response" : "unreachable";
    }
}
