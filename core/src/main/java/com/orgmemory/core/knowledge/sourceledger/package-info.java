/**
 * Canonical source, revision, evidence, processing, upload, and ingestion-job ledger.
 *
 * <p>Retrieval now implements source-owned visibility and embedding-profile ports, so this
 * module no longer depends on Retrieval, Asset, Space, Graph, or Connector. ACL persistence
 * is accessed only through ACL-owned facade contracts; the module remains open while its
 * broader external consumer surface is reduced for closure.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.sourceledger;
