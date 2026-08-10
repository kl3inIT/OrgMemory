/**
 * Canonical source, revision, evidence, processing, upload, and ingestion-job ledger.
 *
 * <p>Retrieval now implements source-owned visibility and embedding-profile ports, so this
 * module no longer depends on Retrieval, Asset, Space, Graph, or Connector. ACL persistence
 * is accessed only through ACL-owned facade contracts. The module is closed so external
 * consumers can depend only on its public API surface. Graph indexing resolves revision state
 * through a Source Ledger-owned immutable query instead of consuming revision persistence.
 * Citation and document opening resolve ready revision and validated blob evidence through
 * immutable queries, keeping both persistence models and their lifecycle enums inside this module.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "knowledge.acl",
            "knowledge::evidence",
            "knowledge::storage",
            "organization",
            "permission",
            "shared",
            "shared::error"
        },
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED)
package com.orgmemory.core.knowledge.sourceledger;
