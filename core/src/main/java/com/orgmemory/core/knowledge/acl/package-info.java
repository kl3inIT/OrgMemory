/**
 * Source ACL evidence, external principals, mappings, and sealed group memberships.
 *
 * <p>Connector ingestion now calls ACL-owned commands and queries, so this module no longer
 * depends on Connector. It remains open while its remaining source-ledger and retrieval seams
 * are replaced with explicit APIs.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.acl;
