package com.orgmemory.graphrag.export;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionNamespace;

/** Bounded, deterministic, permission-aware graph export reader. */
public interface GraphExportReader {

    GraphExportDocument read(
            AuthorizedEvidenceScope scope, ProjectionNamespace namespace);
}
