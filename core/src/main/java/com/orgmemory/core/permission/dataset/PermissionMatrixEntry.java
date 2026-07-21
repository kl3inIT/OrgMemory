package com.orgmemory.core.permission.dataset;

import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.KnowledgeRole;

public record PermissionMatrixEntry(
        KnowledgeClassification classification,
        KnowledgeRole role,
        DatasetPermissionRule rule) {
}
