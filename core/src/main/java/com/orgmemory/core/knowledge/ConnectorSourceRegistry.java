package com.orgmemory.core.knowledge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The source systems this deployment can ingest, collected from the adapters that are on the
 * classpath rather than listed here.
 *
 * <p>That direction is the point. A registry {@code core} wrote out by hand would mean the
 * governed ledger knew the name of every source, and adding one would mean editing the ledger.
 * Adapters declare themselves; this only refuses what nobody declared.
 *
 * <p>Refusing is not a formality. A crawl batch names its own source system, and a batch
 * naming a system no adapter contributed is either a misconfiguration or a forged payload —
 * either way it must not be allowed to write into the ledger under a name nothing governs.
 */
@Service
public class ConnectorSourceRegistry {

    private final Map<String, ConnectorSourceProfile> profiles;

    ConnectorSourceRegistry(List<ConnectorSourceProfile> contributed) {
        Map<String, ConnectorSourceProfile> collected = new LinkedHashMap<>();
        for (ConnectorSourceProfile profile : contributed) {
            ConnectorSourceProfile clash = collected.putIfAbsent(profile.sourceSystem(), profile);
            if (clash != null) {
                throw new IllegalStateException(
                        "Two adapters both claim the source system " + profile.sourceSystem());
            }
        }
        this.profiles = Map.copyOf(collected);
    }

    /**
     * The profile for a crawl's source system.
     *
     * @throws UnsupportedConnectorSourceException when no adapter contributed one
     */
    public ConnectorSourceProfile require(String sourceSystem) {
        ConnectorSourceProfile profile = profiles.get(normalize(sourceSystem));
        if (profile == null) {
            throw new UnsupportedConnectorSourceException(
                    "No connector is installed for the source system " + sourceSystem);
        }
        return profile;
    }

    public boolean supports(String sourceSystem) {
        return profiles.containsKey(normalize(sourceSystem));
    }

    /** Every installed source, for an administration screen that offers them. */
    public List<ConnectorSourceProfile> installed() {
        return List.copyOf(profiles.values());
    }

    private static String normalize(String sourceSystem) {
        return sourceSystem == null ? "" : sourceSystem.trim().toLowerCase();
    }
}
