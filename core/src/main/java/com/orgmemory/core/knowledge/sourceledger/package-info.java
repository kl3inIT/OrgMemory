/**
 * Canonical source, revision, evidence, processing, upload, and ingestion-job ledger.
 *
 * <p>Retrieval now implements source-owned visibility and embedding-profile ports, so this
 * module no longer depends on Retrieval. It remains open while Asset, Space, Graph, ACL, and
 * Connector repository dependencies are replaced with intentional APIs.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.sourceledger;
