package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells a connector which objects a connection already has in retrieval.
 *
 * <p>This exists so a permissions-only crawl does not have to ask the source what its objects
 * are. Access changes far more often than content does — people join and leave channels daily —
 * and re-reading every message body to answer "who can see this now" is the expensive mistake
 * that makes a permission crawl unaffordable. The ledger already knows the objects; the source
 * only needs to be asked who may read them.
 *
 * <p>Reading back what we stored is not a way to discover new objects, and a crawl built on this
 * list must never claim completeness: it would be confirming its own record rather than the
 * source's, and the ledger would then be free to retire whatever that circular answer omitted.
 */
@Service
public class ConnectorObjectDirectory {

    private final SourceObjectRepository sources;

    ConnectorObjectDirectory(SourceObjectRepository sources) {
        this.sources = sources;
    }

    /**
     * The external ids this connection currently has in retrieval, in no particular order.
     *
     * @throws IllegalArgumentException when the source system has no connector profile
     */
    @Transactional(readOnly = true)
    public List<String> activeObjectIds(UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        if (!SlackConnectorProfile.supports(sourceSystem)) {
            throw new IllegalArgumentException("Unsupported connector source system: " + sourceSystem);
        }
        return sources.findActiveExternalObjectIds(
                organizationId, SlackConnectorProfile.SOURCE_TYPE, sourceConnectionKey.trim());
    }
}
