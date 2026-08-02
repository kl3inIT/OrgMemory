/**
 * Authorized search, evidence and citation assembly, retrieval persistence, embedding profiles,
 * query embeddings, and projection identity.
 *
 * <p>This nested module also implements the source-owned visibility and embedding-profile ports.
 * Graph indexing now resolves profiles through the registry instead of profile persistence.
 * Catalog, text-chunk, vector-literal, and projection-namespace values belong to Asset and are
 * consumed here one way. Top-level search consumers cross the parent-owned
 * {@code knowledge::search} interface instead of this implementation package. Graph exploration,
 * export, and curation consume a Retrieval-owned canonical evidence verifier and immutable verified
 * snapshot instead of scope resolution or retrieval-store implementation types. API and Worker
 * adapters inject Retrieval interfaces for canonical/GraphRAG search, citation/source opening,
 * authorization inspection, and embedding-profile resolution; their default/JDBC implementations
 * are package-private. Full evidence-scope resolution, canonical persistence, candidates, and
 * embedding-profile persistence remain internal. Asset, Organization, and Source Ledger citation
 * reads cross owner-defined queries.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "ai",
            "authorization",
            "knowledge.acl",
            "knowledge.asset",
            "knowledge::catalog",
            "knowledge::search",
            "knowledge.sourceledger",
            "knowledge.space",
            "knowledge::storage",
            "organization",
            "permission",
            "shared",
            "shared::error"
        })
package com.orgmemory.core.knowledge.retrieval;
