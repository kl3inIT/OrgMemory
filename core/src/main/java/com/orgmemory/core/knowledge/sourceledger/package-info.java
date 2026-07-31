/**
 * Canonical source, revision, evidence, processing, upload, and ingestion-job ledger.
 *
 * <p>Retrieval now implements source-owned visibility and embedding-profile ports, so this
 * module no longer depends on Retrieval, Asset, Space, Graph, or Connector. It remains open
 * while the ACL repository dependency is replaced with an intentional API.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.sourceledger;
