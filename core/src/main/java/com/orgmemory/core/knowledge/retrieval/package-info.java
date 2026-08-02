/**
 * Authorized search, evidence and citation assembly, retrieval persistence, embedding profiles,
 * query embeddings, and projection identity.
 *
 * <p>This nested module also implements the source-owned visibility and embedding-profile ports.
 * Graph indexing now resolves profiles through the registry instead of profile persistence.
 * Catalog, text-chunk, vector-literal, and projection-namespace values belong to Asset and are
 * consumed here one way. Top-level search consumers cross the parent-owned
 * {@code knowledge::search} interface instead of this implementation package. The module remains
 * open while its Graph verifier and remaining sibling-module adapters are replaced by intentional
 * interfaces during the Knowledge module-closing phase. Asset, Organization, and Source Ledger
 * citation reads already cross owner-defined queries.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.retrieval;
