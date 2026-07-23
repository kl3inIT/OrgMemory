package com.orgmemory.connectors.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The token must reach the client and reach nothing else. */
class ConfiguredSlackCredentialProviderTests {

    private static final String TOKEN = "xoxb-not-a-real-token";
    private static final String CONNECTION = "T-workspace";

    @Test
    void resolvesTheTokenForItsOwnConnection() {
        assertEquals(TOKEN, new ConfiguredSlackCredentialProvider(properties(TOKEN)).botToken(CONNECTION));
    }

    @Test
    void refusesAConnectionItHasNoCredentialFor() {
        SlackCredentialUnavailableException unavailable = assertThrows(
                SlackCredentialUnavailableException.class,
                () -> new ConfiguredSlackCredentialProvider(properties(TOKEN)).botToken("T-somebody-else"));

        assertTrue(unavailable.getMessage().contains("T-somebody-else"));
        assertFalse(unavailable.getMessage().contains(TOKEN), "a failure must not carry the token");
    }

    @Test
    void refusesWhenNoTokenIsConfigured() {
        SlackCredentialUnavailableException unavailable = assertThrows(
                SlackCredentialUnavailableException.class,
                () -> new ConfiguredSlackCredentialProvider(properties("")).botToken(CONNECTION));

        assertTrue(unavailable.getMessage().contains(CONNECTION));
    }

    @Test
    void keepsTheTokenOutOfItsOwnDescription() {
        String described = properties(TOKEN).toString();

        assertFalse(described.contains(TOKEN), "properties reach logs and actuator output");
        assertTrue(described.contains("<redacted>"));
        assertTrue(described.contains(CONNECTION), "everything that is not the secret stays readable");
    }

    @Test
    void staysInertUntilFullyConfigured() {
        assertFalse(properties(TOKEN).isRunnable(), "disabled by default");
        assertFalse(
                new SlackConnectorProperties(
                                true, "", CONNECTION, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                List.of(), null, null)
                        .isRunnable(),
                "a missing token is not a runnable connection");
        assertTrue(
                new SlackConnectorProperties(
                                true, TOKEN, CONNECTION, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                List.of(), null, null)
                        .isRunnable());
    }

    private static SlackConnectorProperties properties(String token) {
        return new SlackConnectorProperties(
                null, token, CONNECTION, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(), null, null);
    }
}
