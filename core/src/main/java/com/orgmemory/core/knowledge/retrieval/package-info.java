/**
 * Authorized search, evidence and citation assembly, retrieval persistence, embedding profiles,
 * query embeddings, and projection identity.
 *
 * <p>This nested module also implements the source-owned visibility and embedding-profile ports.
 * Graph indexing now resolves profiles through the registry instead of profile persistence.
 * Catalog, text-chunk, vector-literal, and projection-namespace values belong to Asset and are
 * consumed here one way. The module remains open while its broader sibling-module consumer surface
 * and direct Asset persistence access are replaced by intentional interfaces during the Knowledge
 * module-closing phase.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.retrieval;
