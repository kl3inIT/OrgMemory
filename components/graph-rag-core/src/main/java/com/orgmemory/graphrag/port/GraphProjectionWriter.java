package com.orgmemory.graphrag.port;

import java.util.UUID;

public interface GraphProjectionWriter {

    void replaceRevision(GraphRevisionContributions contributions);

    void removeRevision(UUID organizationId, UUID sourceRevisionId);
}
