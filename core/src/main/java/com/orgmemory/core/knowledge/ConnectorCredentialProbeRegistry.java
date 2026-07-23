package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.secret.SecretValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The credential probes adapters contributed, keyed by source system.
 *
 * <p>Separate from {@link ConnectorSourceRegistry} because probing is optional. A source can be
 * ingestible without being checkable — an adapter that reads a directory on disk has no
 * credential to test — and folding the two together would either force every adapter to write a
 * probe it does not need or let a missing profile hide behind a present probe.
 */
@Service
public class ConnectorCredentialProbeRegistry {

    private final Map<String, ConnectorCredentialProbe> probes;

    ConnectorCredentialProbeRegistry(List<ConnectorCredentialProbe> contributed) {
        Map<String, ConnectorCredentialProbe> collected = new LinkedHashMap<>();
        for (ConnectorCredentialProbe probe : contributed) {
            ConnectorCredentialProbe clash =
                    collected.putIfAbsent(normalize(probe.sourceSystem()), probe);
            if (clash != null) {
                throw new IllegalStateException(
                        "Two adapters both probe the source system " + probe.sourceSystem());
            }
        }
        this.probes = Map.copyOf(collected);
    }

    /**
     * Checks a credential with the adapter that knows how.
     *
     * @throws UnsupportedConnectorSourceException when the source contributed no probe, which is
     *         a different fact from the credential being bad and is reported as one
     */
    public ConnectorCredentialProbeResult probe(String sourceSystem, SecretValue credential) {
        ConnectorCredentialProbe probe = probes.get(normalize(sourceSystem));
        if (probe == null) {
            throw new UnsupportedConnectorSourceException(
                    "Checking a credential is not supported for the source system " + sourceSystem);
        }
        return probe.probe(credential);
    }

    public boolean supports(String sourceSystem) {
        return probes.containsKey(normalize(sourceSystem));
    }

    private static String normalize(String sourceSystem) {
        return sourceSystem == null ? "" : sourceSystem.trim().toLowerCase();
    }
}
