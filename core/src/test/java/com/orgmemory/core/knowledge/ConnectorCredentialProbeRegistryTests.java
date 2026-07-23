package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.shared.secret.SecretValue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves that checking a credential goes to the adapter that knows how, and to no other.
 *
 * <p>This is the seam that used to be a name in a controller: the administration API refused
 * every source but Slack, in an {@code if}. Selection lives here now, so the API asks a
 * question rather than making a decision, and the decision is testable without a web layer.
 */
class ConnectorCredentialProbeRegistryTests {

    private static ConnectorCredentialProbe probeFor(String sourceSystem, String reportedKey) {
        return new ConnectorCredentialProbe() {
            @Override
            public String sourceSystem() {
                return sourceSystem;
            }

            @Override
            public ConnectorCredentialProbeResult probe(SecretValue credential) {
                return ConnectorCredentialProbeResult.usable(reportedKey, sourceSystem, "someone");
            }
        };
    }

    @Test
    void aCredentialIsCheckedByTheAdapterThatClaimedItsSource() {
        var registry = new ConnectorCredentialProbeRegistry(
                List.of(probeFor("slack", "T0WORKSPACE"), probeFor("google_drive", "example.com")));

        assertEquals(
                "T0WORKSPACE",
                registry.probe("slack", SecretValue.of("anything")).connectionKey(),
                "the Slack probe answers for Slack");
        assertEquals(
                "example.com",
                registry.probe("google_drive", SecretValue.of("anything")).connectionKey(),
                "and a second adapter answers for its own source, with no code between them");
    }

    @Test
    void aSourceWithNoProbeIsRefusedRatherThanAnswered() {
        var registry = new ConnectorCredentialProbeRegistry(List.of(probeFor("slack", "T0WORKSPACE")));

        // Not the same fact as a bad credential, and reported as a different one: answering
        // "invalid" here would send an administrator to re-check a credential that is fine.
        assertThrows(
                UnsupportedConnectorSourceException.class,
                () -> registry.probe("google_drive", SecretValue.of("anything")));
        assertFalse(registry.supports("google_drive"));
        assertTrue(registry.supports("slack"));
    }

    @Test
    void twoAdaptersCannotBothProbeOneSource() {
        assertThrows(
                IllegalStateException.class,
                () -> new ConnectorCredentialProbeRegistry(
                        List.of(probeFor("slack", "one"), probeFor("slack", "two"))),
                "which of the two answered would otherwise depend on bean ordering");
    }

    @Test
    void theSourceSystemIsMatchedTheWayTheLedgerSpellsIt() {
        var registry = new ConnectorCredentialProbeRegistry(List.of(probeFor("Slack", "T0WORKSPACE")));

        assertTrue(registry.supports("slack"), "an adapter's capitalisation is not a second source");
        assertTrue(registry.supports("  SLACK  "));
    }
}
