package com.orgmemory.core.knowledge.sourceledger;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Source Ledger-owned publication mutation used inside an existing publication transaction. */
@Service
public class SourcePublicationService {

    private final SourceObjectRepository sources;

    public SourcePublicationService(SourceObjectRepository sources) {
        this.sources = sources;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishRevision(PublishSourceRevisionCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.organizationId(), "organizationId");
        Objects.requireNonNull(command.sourceObjectId(), "sourceObjectId");
        Objects.requireNonNull(command.sourceRevisionId(), "sourceRevisionId");

        SourceObject source = sources.findById(command.sourceObjectId())
                .filter(candidate -> candidate.getOrganizationId()
                        .equals(command.organizationId()))
                .orElseThrow(() -> new IllegalStateException(
                        "Publication source object is missing"));
        source.publishRevision(command.sourceRevisionId());
        sources.save(source);
    }
}
