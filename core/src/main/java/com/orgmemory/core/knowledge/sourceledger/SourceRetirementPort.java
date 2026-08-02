package com.orgmemory.core.knowledge.sourceledger;

import java.util.UUID;

/** Source Ledger-owned public contract used to coordinate published upload retirement. */
public interface SourceRetirementPort {

    ReadyManualUploadRef requireReadyManualUpload(UUID organizationId, UUID sourceId);

    void archiveReadyManualUpload(UUID organizationId, UUID sourceId, UUID expectedAssetId);
}
