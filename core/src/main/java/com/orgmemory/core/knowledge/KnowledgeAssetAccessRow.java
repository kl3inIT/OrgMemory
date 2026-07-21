package com.orgmemory.core.knowledge;

import java.util.UUID;

interface KnowledgeAssetAccessRow {

    UUID getId();

    UUID getDepartmentId();

    String getClassification();

    String getDeclaredAccess();

    String getOrgMemoryGate();

    UUID getIngestionSnapshotId();

    UUID getCurrentSnapshotId();
}
