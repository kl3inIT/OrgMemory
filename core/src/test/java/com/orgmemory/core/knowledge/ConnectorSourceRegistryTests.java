package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The ledger governs what adapters declared and nothing else.
 *
 * <p>This is the separation the increment exists for: {@code core} holds no list of sources, so
 * a crawl naming a system nothing contributed has no profile, no classification, and no
 * authority — and must be refused rather than written under a name nothing governs.
 */
class ConnectorSourceRegistryTests {

    private static ConnectorSourceProfile profile(String sourceSystem) {
        return new ConnectorSourceProfile(
                sourceSystem,
                sourceSystem,
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES,
                "message",
                "text/plain");
    }

    @Test
    void resolvesWhatAnAdapterContributed() {
        var registry = new ConnectorSourceRegistry(List.of(profile("slack")));

        assertTrue(registry.supports("slack"));
        assertEquals("slack", registry.require("SLACK").sourceSystem(), "the name is matched case-insensitively");
        assertEquals(List.of("slack"), registry.installed().stream().map(ConnectorSourceProfile::sourceSystem).toList());
    }

    @Test
    void refusesASourceNobodyInstalled() {
        var registry = new ConnectorSourceRegistry(List.of(profile("slack")));

        assertFalse(registry.supports("teams"));
        var refused = assertThrows(
                UnsupportedConnectorSourceException.class, () -> registry.require("teams"));
        assertTrue(refused.getMessage().contains("teams"));
    }

    @Test
    void refusesTwoAdaptersClaimingOneSourceSystem() {
        // Silently keeping one of them would mean the classification a crawl is governed by
        // depended on bean ordering.
        var clash = assertThrows(
                IllegalStateException.class,
                () -> new ConnectorSourceRegistry(List.of(profile("slack"), profile("slack"))));

        assertTrue(clash.getMessage().contains("slack"));
    }

    @Test
    void everythingACrawlerProducesIsGovernedByItsSource() {
        assertEquals(
                AclAuthority.SOURCE,
                profile("slack").aclAuthority(),
                "a source OrgMemory were the record for would not be a connector");
    }
}
