package com.orgmemory.core.knowledge.sourceledger;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Source Ledger-owned lifecycle commands for canonical source objects. */
@Service
public class SourceLifecycleService {

    private final SourceObjectRepository sources;

    SourceLifecycleService(SourceObjectRepository sources) {
        this.sources = sources;
    }

    /** Retires an active source while retaining its evidence and history. */
    @Transactional
    public boolean retire(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalObjectId) {
        var existing = sources
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        Objects.requireNonNull(organizationId, "organizationId"),
                        Objects.requireNonNull(sourceSystem, "sourceSystem"),
                        Objects.requireNonNull(sourceConnectionKey, "sourceConnectionKey"),
                        Objects.requireNonNull(externalObjectId, "externalObjectId"));
        if (existing.isEmpty() || existing.get().getStatus() != SourceObjectStatus.ACTIVE) {
            return false;
        }
        SourceObject source = existing.get();
        source.archive();
        sources.saveAndFlush(source);
        return true;
    }
}
